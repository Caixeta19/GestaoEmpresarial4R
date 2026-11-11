package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.response.BipagemResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.InventarioItemResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.InventarioResumoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.TotaisInventarioDTO;
import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.enums.StatusInventario;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.model.InventarioExecucao;
import com.vivo4redes.syscor.estoque.model.InventarioItem;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.estoque.repository.InventarioExecucaoRepository;
import com.vivo4redes.syscor.estoque.repository.InventarioItemRepository;
import com.vivo4redes.syscor.exception.InventarioAbertoException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.FilialRepository;
import com.vivo4redes.syscor.venda.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario.*;

/**
 * Inventário por filial: IQ09 (SAP) x estoque do sistema x contagem física (bipagem).
 * <p>
 * O cruzamento é exato, pelo serial normalizado ({@link SerialInventario}); a classificação está em
 * {@link InventarioClassificador}. Pendente = esperado (IQ09 da loja ∪ estoque da loja) e ainda não bipado.
 */
@Service
@RequiredArgsConstructor
public class InventarioService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm").withZone(FUSO);
    private static final DateTimeFormatter DATA_HORA_CSV = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(FUSO);
    private static final List<StatusInventario> EM_ANDAMENTO = List.of(StatusInventario.ABERTO, StatusInventario.EM_CONTAGEM);
    /** Estoque do sistema que se espera achar na loja. */
    private static final List<StatusEstoque> STATUS_ESPERADOS_SISTEMA =
            List.of(StatusEstoque.DISPONIVEL, StatusEstoque.RESERVADO, StatusEstoque.DEFEITO);
    private static final int LOTE_CONSULTA = 1000;
    private static final int TAMANHO_MAXIMO_PAGINA = 500;

    private static final String SQL_BIPAGEM = """
            INSERT INTO tb_inventario_item
                (execucao_id, serial_normalizado, serial_sap, resultado, esperado_iq09, esperado_sistema, bipado_em, bipado_por_id)
            VALUES (:execucao, :serial, :lido, 'SOBRA_FISICA', FALSE, FALSE, :agora, :usuario)
            ON CONFLICT (execucao_id, serial_normalizado) DO UPDATE
                SET bipado_em = EXCLUDED.bipado_em, bipado_por_id = EXCLUDED.bipado_por_id
                WHERE tb_inventario_item.bipado_em IS NULL
            RETURNING id, (xmax = 0) AS inserido
            """;

    private final RelatorioSapParser relatorioSapParser;
    private final EstoqueGeralRepository estoqueGeralRepository;
    private final InventarioExecucaoRepository execucaoRepository;
    private final InventarioItemRepository itemRepository;
    private final FilialRepository filialRepository;
    private final UsuarioService usuarioService;
    private final NamedParameterJdbcTemplate jdbc;

    // ======================================================================
    // Abrir
    // ======================================================================

    /**
     * Abre o inventário da filial com a IQ09: só entram as linhas do centro (e depósito, se cadastrado) da filial.
     * Esperados = IQ09 da filial (status usuário ADEP ou vazio) ∪ estoque da filial (DISPONIVEL, RESERVADO, DEFEITO).
     */
    @Transactional
    public InventarioResumoResponseDTO abrir(Long filialId, MultipartFile arquivo) {
        Filial filial = filialRepository.findById(filialId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Filial"));
        if (filial.getCentroSap() == null || filial.getCentroSap().isBlank()) {
            throw new NegocioException("Cadastre o centro SAP da filial antes de abrir o inventário");
        }
        execucaoRepository.findFirstByFilialIdAndStatusIn(filialId, EM_ANDAMENTO)
                .ifPresent(aberta -> {
                    throw new InventarioAbertoException(aberta.getId());
                });

        List<LinhaSapDTO> linhas = relatorioSapParser.parsear(arquivo);
        if (linhas.isEmpty()) {
            throw new NegocioException("A IQ09 não tem nenhuma linha de dados.");
        }

        String centro = filial.getCentroSap().trim().toUpperCase(Locale.ROOT);
        String deposito = filial.getDepositoSap() == null ? "" : filial.getDepositoSap().trim().toUpperCase(Locale.ROOT);
        Set<String> centrosCadastrados = new HashSet<>(filialRepository.listarCentrosSap());

        Map<String, InventarioItem> itens = new LinkedHashMap<>();
        List<Object[]> outrosCentros = new ArrayList<>();
        SortedSet<String> centrosSemFilial = new TreeSet<>();
        int semSerial = 0;
        int outroCentro = 0;

        for (LinhaSapDTO linha : linhas) {
            String serial = SerialInventario.normalizar(linha.serial());
            if (serial.isBlank()) {
                semSerial++;
                continue;
            }
            boolean daFilial = centro.equals(linha.centro()) && (deposito.isEmpty() || deposito.equals(linha.deposito()));
            if (!daFilial) {
                outroCentro++;
                if (!linha.centro().isBlank() && !centrosCadastrados.contains(linha.centro())) {
                    centrosSemFilial.add(linha.centro());
                }
                outrosCentros.add(new Object[]{serial, linha.centro(), linha.deposito(), linha.statusUsuario()});
                continue;
            }
            itens.putIfAbsent(serial, itemDaIq09(linha, serial));
        }

        // Estoque do sistema da filial (filial_estoque guarda o nome da filial).
        for (EstoqueGeral unidade : estoqueGeralRepository.findByFilialEstoqueAndStatusIn(filial.getNome(), STATUS_ESPERADOS_SISTEMA)) {
            String serial = SerialInventario.normalizar(unidade.getSerial());
            if (serial.isBlank()) continue;
            InventarioItem item = itens.computeIfAbsent(serial, s -> InventarioItem.builder()
                    .serialNormalizado(s).serialSap(unidade.getSerial()).build());
            item.setEsperadoSistema(true);
            preencherEstoque(item, unidade);
        }

        // Seriais da IQ09 que não estão no estoque da filial: procura em qualquer filial/status (VENDIDO, outra loja).
        List<String> semEstoque = itens.values().stream()
                .filter(i -> i.getItemEstoqueId() == null).map(InventarioItem::getSerialNormalizado).toList();
        for (EstoqueGeral unidade : buscarEstoquePorSeriais(semEstoque)) {
            InventarioItem item = itens.get(SerialInventario.normalizar(unidade.getSerial()));
            if (item != null && item.getItemEstoqueId() == null) {
                preencherEstoque(item, unidade);
            }
        }

        Usuario usuario = usuarioService.usuarioLogado();
        InventarioExecucao execucao = execucaoRepository.save(InventarioExecucao.builder()
                .filial(filial)
                .status(StatusInventario.ABERTO)
                .nomeArquivo(arquivo.getOriginalFilename())
                .totalLinhasSap(linhas.size())
                .linhasIgnoradasOutroCentro(outroCentro)
                .linhasSemSerial(semSerial)
                .centrosSemFilial(limitar(String.join(", ", centrosSemFilial), 2000))
                .abertoPor(usuario)
                .executadoPor(usuario.getNome())
                .criadoEm(Instant.now())
                .build());

        for (InventarioItem item : itens.values()) {
            item.setExecucao(execucao);
            InventarioClassificador.classificar(item, false);
        }
        itemRepository.saveAll(itens.values());
        gravarOutrosCentros(execucao.getId(), outrosCentros);
        itemRepository.flush();

        return detalhar(execucao, null, null, 0, 50);
    }

    private InventarioItem itemDaIq09(LinhaSapDTO linha, String serial) {
        return InventarioItem.builder()
                .serialNormalizado(serial)
                .serialSap(limitar(linha.serial(), 50))
                .materialSap(limitar(linha.material(), 40))
                .denominacaoSap(limitar(linha.denominacao(), 200))
                .centroSap(limitar(linha.centro(), 20))
                .depositoSap(limitar(linha.deposito(), 20))
                .tipoEstoqueSap(limitar(linha.tipoEstoque(), 10))
                .statusSap(limitar(linha.statusSistema(), 30))
                .statusUsuarioSap(limitar(linha.statusUsuario(), 30))
                .categoriaEquipamento(limitar(linha.categoriaEquipamento(), 10))
                .modificadoEmSap(linha.modificadoEm())
                .modificadoPorSap(limitar(linha.modificadoPor(), 40))
                .esperadoIq09(InventarioClassificador.statusUsuarioDeEstoqueDaLoja(linha.statusUsuario()))
                .build();
    }

    private static void preencherEstoque(InventarioItem item, EstoqueGeral unidade) {
        item.setItemEstoqueId(unidade.getId());
        item.setStatusSistema(unidade.getStatus() == null ? null : unidade.getStatus().name());
        item.setFilialSistema(limitar(unidade.getFilialEstoque(), 100));
        if (item.getDenominacaoSap() == null || item.getDenominacaoSap().isBlank()) {
            item.setDenominacaoSap(limitar(unidade.getModeloComercial(), 200));
        }
    }

    private List<EstoqueGeral> buscarEstoquePorSeriais(Collection<String> seriais) {
        List<String> lista = new ArrayList<>(seriais);
        List<EstoqueGeral> encontrados = new ArrayList<>();
        for (int i = 0; i < lista.size(); i += LOTE_CONSULTA) {
            encontrados.addAll(estoqueGeralRepository.findBySerialIn(lista.subList(i, Math.min(i + LOTE_CONSULTA, lista.size()))));
        }
        return encontrados;
    }

    private void gravarOutrosCentros(Long execucaoId, List<Object[]> linhas) {
        if (linhas.isEmpty()) return;
        String sql = """
                INSERT INTO tb_inventario_serial_outro_centro (execucao_id, serial_normalizado, centro_sap, deposito_sap, status_usuario_sap)
                VALUES (?, ?, ?, ?, ?)
                """;
        List<Object[]> parametros = linhas.stream()
                .map(l -> new Object[]{execucaoId, limitar((String) l[0], 60), limitar((String) l[1], 20),
                        limitar((String) l[2], 20), limitar((String) l[3], 30)})
                .toList();
        jdbc.getJdbcTemplate().batchUpdate(sql, parametros);
    }

    // ======================================================================
    // Bipar / desfazer / justificar
    // ======================================================================

    /**
     * Registra a leitura. O UNIQUE (execucao_id, serial_normalizado) + ON CONFLICT garante que o mesmo serial só conta
     * uma vez, mesmo com dois celulares bipando ao mesmo tempo; o segundo recebe {@code jaBipado}.
     */
    @Transactional
    public BipagemResponseDTO bipar(Long execucaoId, String serialLido) {
        String serial = SerialInventario.normalizar(serialLido);
        if (serial.isBlank()) {
            throw new NegocioException("Informe o serial/IMEI lido.");
        }
        Usuario usuario = usuarioService.usuarioLogado();

        // Primeiro a troca ABERTO -> EM_CONTAGEM (trava exclusiva curta), depois a trava compartilhada da bipagem.
        jdbc.update("UPDATE tb_inventario_execucao SET status = 'EM_CONTAGEM' WHERE id = :id AND status = 'ABERTO'",
                Map.of("id", execucaoId));
        InventarioExecucao execucao = execucaoRepository.buscarParaBipagem(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inventário"));
        exigirEmAndamento(execucao);

        List<Map<String, Object>> gravado = jdbc.queryForList(SQL_BIPAGEM, new MapSqlParameterSource()
                .addValue("execucao", execucaoId)
                .addValue("serial", serial)
                .addValue("lido", limitar(serialLido.trim(), 50))
                .addValue("agora", LocalDateTime.now(FUSO))
                .addValue("usuario", usuario.getId()));

        InventarioItem item = itemRepository.findByExecucaoIdAndSerialNormalizado(execucaoId, serial)
                .orElseThrow(() -> new IllegalStateException("Bipagem gravada e não encontrada: " + serial));

        boolean jaBipado = gravado.isEmpty();
        if (!jaBipado) {
            boolean inserido = Boolean.TRUE.equals(gravado.get(0).get("inserido"));
            if (inserido) {
                // Fora da lista de esperados: procura no estoque de qualquer filial para explicar a sobra.
                estoqueGeralRepository.findBySerial(serial).ifPresent(unidade -> preencherEstoque(item, unidade));
            }
            InventarioClassificador.classificar(item, false);
            itemRepository.save(item);
        }

        String bipadoPor = item.getBipadoPor() == null ? null : item.getBipadoPor().getNome();
        String mensagem = jaBipado
                ? "Já bipado às " + HORA.format(item.getBipadoEm()) + (bipadoPor == null ? "" : " por " + bipadoPor) + "."
                : item.getObservacao();
        return new BipagemResponseDTO(item.getId(), item.getSerialSap(), item.getResultado(), item.getDenominacaoSap(),
                mensagem, jaBipado, item.getBipadoEm(), bipadoPor, totais(execucaoId));
    }

    /** Desfaz uma bipagem errada: o item volta a pendente; sobra que só existia pela leitura é apagada. */
    @Transactional
    public TotaisInventarioDTO desfazerBipagem(Long execucaoId, Long itemId) {
        InventarioExecucao execucao = execucaoRepository.buscarParaBipagem(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inventário"));
        exigirEmAndamento(execucao);
        InventarioItem item = itemRepository.findByIdAndExecucaoId(itemId, execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item do inventário"));
        if (!item.bipado()) {
            throw new NegocioException("Este item não está bipado.");
        }
        boolean soDaLeitura = !item.esperado() && (item.getCentroSap() == null || item.getCentroSap().isBlank());
        if (soDaLeitura) {
            itemRepository.delete(item);
        } else {
            item.setBipadoEm(null);
            item.setBipadoPor(null);
            InventarioClassificador.classificar(item, false);
            itemRepository.save(item);
        }
        itemRepository.flush();
        return totais(execucaoId);
    }

    @Transactional
    public InventarioItemResponseDTO justificar(Long execucaoId, Long itemId, String texto) {
        InventarioExecucao execucao = execucaoRepository.buscarParaBipagem(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inventário"));
        exigirEmAndamento(execucao);
        InventarioItem item = itemRepository.findByIdAndExecucaoId(itemId, execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item do inventário"));
        if (item.getResultado() != PENDENTE) {
            throw new NegocioException("Só itens pendentes recebem justificativa.");
        }
        String justificativa = texto == null ? "" : texto.trim();
        item.setJustificativa(justificativa.isEmpty() ? null : limitar(justificativa, 300));
        return InventarioItemResponseDTO.from(itemRepository.save(item));
    }

    // ======================================================================
    // Fechar / cancelar
    // ======================================================================

    /** Fecha só sem pendentes sem justificativa; os pendentes justificados viram FALTA (IQ09) ou SO_NO_SISTEMA. */
    @Transactional
    public void fechar(Long execucaoId) {
        InventarioExecucao execucao = execucaoRepository.buscarParaFechamento(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inventário"));
        exigirEmAndamento(execucao);
        long semJustificativa = itemRepository.countByExecucaoIdAndResultadoAndJustificativaIsNull(execucaoId, PENDENTE);
        if (semJustificativa > 0) {
            throw new NegocioException("Há " + semJustificativa + (semJustificativa == 1 ? " item pendente" : " itens pendentes")
                    + " sem justificativa: bipe ou justifique antes de fechar o inventário.");
        }
        List<InventarioItem> itens = itemRepository.findByExecucaoId(execucaoId);
        itens.forEach(item -> InventarioClassificador.classificar(item, true));
        itemRepository.saveAll(itens);
        itemRepository.flush();

        execucao.setStatus(StatusInventario.FECHADO);
        execucao.setFechadoPor(usuarioService.usuarioLogado());
        execucao.setFechadoEm(Instant.now());
        gravarTotais(execucao, totais(execucaoId));
        execucaoRepository.save(execucao);
    }

    @Transactional
    public InventarioResumoResponseDTO cancelar(Long execucaoId) {
        InventarioExecucao execucao = execucaoRepository.buscarParaFechamento(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inventário"));
        exigirEmAndamento(execucao);
        execucao.setStatus(StatusInventario.CANCELADO);
        execucao.setFechadoPor(usuarioService.usuarioLogado());
        execucao.setFechadoEm(Instant.now());
        gravarTotais(execucao, totais(execucaoId));
        execucaoRepository.save(execucao);
        return InventarioResumoResponseDTO.from(execucao, totais(execucaoId), null, null);
    }

    private void gravarTotais(InventarioExecucao e, TotaisInventarioDTO t) {
        Map<String, Long> r = t.porResultado();
        e.setTotalEsperados((int) t.esperados());
        e.setTotalBipados((int) t.bipados());
        e.setTotalPendentes((int) t.pendentes());
        e.setTotalConferidos(r.get(CONFERIDO.name()).intValue());
        e.setTotalFalta(r.get(FALTA.name()).intValue());
        e.setTotalSemEntradaSistema(r.get(SEM_ENTRADA_SISTEMA.name()).intValue());
        e.setTotalSobraFisica(r.get(SOBRA_FISICA.name()).intValue());
        e.setTotalVendidoEmEstoque(r.get(VENDIDO_EM_ESTOQUE.name()).intValue());
        e.setTotalSoNoSistema(r.get(SO_NO_SISTEMA.name()).intValue());
        e.setTotalStatusSapDivergente(r.get(STATUS_SAP_DIVERGENTE.name()).intValue());
    }

    // ======================================================================
    // Consultas
    // ======================================================================

    @Transactional(readOnly = true)
    public List<InventarioResumoResponseDTO> listar(Long filialId, StatusInventario status) {
        Collection<StatusInventario> filtro = status == null ? EnumSet.allOf(StatusInventario.class) : List.of(status);
        return execucaoRepository.listar(filialId, filtro).stream()
                .map(e -> InventarioResumoResponseDTO.from(e, totais(e.getId()), null, null))
                .toList();
    }

    /**
     * Resumo, contadores, uma página de itens (filtrada por resultado e busca) e as últimas 10 bipagens.
     *
     * @param resultados vazio ou nulo = todos
     */
    @Transactional(readOnly = true)
    public InventarioResumoResponseDTO buscar(Long execucaoId, Collection<ResultadoConciliacaoInventario> resultados,
                                              String busca, int pagina, int tamanho) {
        InventarioExecucao execucao = execucaoRepository.findById(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inventário"));
        return detalhar(execucao, resultados, busca, pagina, tamanho);
    }

    private InventarioResumoResponseDTO detalhar(InventarioExecucao execucao, Collection<ResultadoConciliacaoInventario> resultados,
                                                 String busca, int pagina, int tamanho) {
        Collection<ResultadoConciliacaoInventario> filtro = resultados == null || resultados.isEmpty()
                ? EnumSet.allOf(ResultadoConciliacaoInventario.class) : resultados;
        String termo = busca == null || busca.isBlank() ? null : "%" + busca.trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "") + "%";
        int tamanhoPagina = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO_PAGINA);
        Page<InventarioItem> page = itemRepository.buscarItens(execucao.getId(), filtro, termo,
                PageRequest.of(Math.max(pagina, 0), tamanhoPagina));
        var itens = new InventarioResumoResponseDTO.PaginaItens(
                page.getContent().stream().map(InventarioItemResponseDTO::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        var ultimas = itemRepository.ultimasBipagens(execucao.getId(), PageRequest.of(0, 10)).stream()
                .map(InventarioItemResponseDTO::from).toList();
        return InventarioResumoResponseDTO.from(execucao, totais(execucao.getId()), itens, ultimas);
    }

    /** Contadores ao vivo (várias pessoas bipando ao mesmo tempo): sempre a partir dos itens. */
    @Transactional(readOnly = true)
    public TotaisInventarioDTO totais(Long execucaoId) {
        Map<String, Long> porResultado = new LinkedHashMap<>();
        for (ResultadoConciliacaoInventario r : ResultadoConciliacaoInventario.values()) {
            porResultado.put(r.name(), 0L);
        }
        long esperados = 0;
        long bipados = 0;
        for (Object[] linha : itemRepository.contarPorResultado(execucaoId)) {
            porResultado.put(((ResultadoConciliacaoInventario) linha[0]).name(), ((Number) linha[1]).longValue());
            esperados += ((Number) linha[2]).longValue();
            bipados += ((Number) linha[3]).longValue();
        }
        long divergencias = ResultadoConciliacaoInventario.DIVERGENTES.stream()
                .filter(r -> r != SOBRA_FISICA)
                .mapToLong(r -> porResultado.get(r.name()))
                .sum();
        return new TotaisInventarioDTO(esperados, bipados, porResultado.get(PENDENTE.name()),
                porResultado.get(CONFERIDO.name()), porResultado.get(SOBRA_FISICA.name()), divergencias, porResultado);
    }

    // ======================================================================
    // Exportação
    // ======================================================================

    /** CSV (;, UTF-8 com BOM para o Excel) com todas as linhas e a coluna resultado. */
    @Transactional(readOnly = true)
    public byte[] exportarCsv(Long execucaoId) {
        execucaoRepository.findById(execucaoId).orElseThrow(() -> new RecursoNaoEncontradoException("Inventário"));
        StringBuilder csv = new StringBuilder("﻿");
        csv.append(String.join(";", "Serial", "Material", "Descrição", "Centro", "Depósito", "Status do sistema SAP",
                "Status usuário SAP", "Esperado IQ09", "Esperado sistema", "Status no sistema", "Filial no sistema",
                "Bipado em", "Bipado por", "Resultado", "Observação", "Justificativa", "Causa provável (IA)",
                "Confiança (IA)", "Ação sugerida (IA)")).append('\n');
        for (InventarioItem i : itemRepository.listarParaExportacao(execucaoId)) {
            csv.append(String.join(";",
                    celula(i.getSerialSap()), celula(i.getMaterialSap()), celula(i.getDenominacaoSap()),
                    celula(i.getCentroSap()), celula(i.getDepositoSap()), celula(i.getStatusSap()),
                    celula(i.getStatusUsuarioSap()), i.isEsperadoIq09() ? "Sim" : "Não", i.isEsperadoSistema() ? "Sim" : "Não",
                    celula(i.getStatusSistema()), celula(i.getFilialSistema()),
                    i.getBipadoEm() == null ? "" : DATA_HORA_CSV.format(i.getBipadoEm()),
                    celula(i.getBipadoPor() == null ? null : i.getBipadoPor().getNome()),
                    i.getResultado().name(), celula(i.getObservacao()), celula(i.getJustificativa()),
                    celula(com.vivo4redes.syscor.estoque.service.diagnostico.CausaProvavelIa.rotuloDe(i.getCausaProvavel())),
                    celula(i.getConfiancaIa()), celula(i.getAcaoSugerida()))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    /** Sem ; nem quebra de linha dentro da célula; serial longo vai como texto ("="...) para o Excel não arredondar. */
    private static String celula(String valor) {
        if (valor == null) return "";
        String limpo = valor.replace(';', ',').replaceAll("[\\r\\n]+", " ").trim();
        return limpo.matches("\\d{12,}") ? "=\"" + limpo + "\"" : limpo;
    }

    // ======================================================================

    private static void exigirEmAndamento(InventarioExecucao execucao) {
        if (!execucao.getStatus().emAndamento()) {
            throw new NegocioException("O inventário #" + execucao.getId() + " está " + execucao.getStatus()
                    + ": não aceita mais alterações.");
        }
    }

    private static String limitar(String texto, int tamanho) {
        if (texto == null) return null;
        return texto.length() > tamanho ? texto.substring(0, tamanho) : texto;
    }

    /** Usado pelo diagnóstico: centros da mesma IQ09 onde o serial aparece. */
    @Transactional(readOnly = true)
    public Map<String, List<String>> outrosCentros(Long execucaoId, Collection<String> seriais) {
        if (seriais.isEmpty()) return Map.of();
        Map<String, List<String>> porSerial = new HashMap<>();
        List<String> lista = new ArrayList<>(seriais);
        for (int i = 0; i < lista.size(); i += LOTE_CONSULTA) {
            jdbc.query("""
                    SELECT serial_normalizado, centro_sap, deposito_sap, status_usuario_sap
                    FROM tb_inventario_serial_outro_centro
                    WHERE execucao_id = :execucao AND serial_normalizado IN (:seriais)
                    """, new MapSqlParameterSource()
                    .addValue("execucao", execucaoId)
                    .addValue("seriais", lista.subList(i, Math.min(i + LOTE_CONSULTA, lista.size()))), rs -> {
                String descricao = rs.getString("centro_sap")
                        + (rs.getString("deposito_sap") == null || rs.getString("deposito_sap").isBlank() ? "" : "/" + rs.getString("deposito_sap"))
                        + (rs.getString("status_usuario_sap") == null || rs.getString("status_usuario_sap").isBlank()
                        ? "" : " (" + rs.getString("status_usuario_sap") + ")");
                porSerial.computeIfAbsent(rs.getString("serial_normalizado"), k -> new ArrayList<>()).add(descricao);
            });
        }
        return porSerial.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
