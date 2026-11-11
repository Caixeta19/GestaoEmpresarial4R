package com.vivo4redes.syscor.estoque.service.diagnostico;

import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;
import com.vivo4redes.syscor.estoque.enums.StatusInventario;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.model.InventarioExecucao;
import com.vivo4redes.syscor.estoque.model.InventarioItem;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.estoque.repository.InventarioExecucaoRepository;
import com.vivo4redes.syscor.estoque.repository.InventarioItemRepository;
import com.vivo4redes.syscor.estoque.service.InventarioService;
import com.vivo4redes.syscor.estoque.service.SerialInventario;
import com.vivo4redes.syscor.exception.DiagnosticoIaException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import java.util.*;

/**
 * Diagnóstico das faltas e divergências do inventário fechado pela IA. A IA não decide se um IMEI bate (isso é o
 * cruzamento exato); ela só sugere a causa provável do que já foi classificado.
 * <p>
 * A chamada à API fica fora de transação: o contexto é lido numa transação curta e o resultado gravado em outra.
 */
@Service
public class InventarioDiagnosticoService {

    private static final Logger log = LoggerFactory.getLogger(InventarioDiagnosticoService.class);
    static final String SEM_CHAVE = "Diagnóstico indisponível: configure a chave da API";
    private static final int LOTE_CONSULTA = 1000;

    private final DiagnosticoIaClient cliente;
    private final InventarioExecucaoRepository execucaoRepository;
    private final InventarioItemRepository itemRepository;
    private final EstoqueGeralRepository estoqueGeralRepository;
    private final InventarioService inventarioService;
    private final NamedParameterJdbcTemplate jdbc;
    private final TransactionTemplate leitura;
    private final TransactionTemplate escrita;
    private final int tamanhoLote;

    public InventarioDiagnosticoService(DiagnosticoIaClient cliente,
                                        InventarioExecucaoRepository execucaoRepository,
                                        InventarioItemRepository itemRepository,
                                        EstoqueGeralRepository estoqueGeralRepository,
                                        InventarioService inventarioService,
                                        NamedParameterJdbcTemplate jdbc,
                                        PlatformTransactionManager transacoes,
                                        @Value("${syscor.ia.lote:100}") int tamanhoLote) {
        this.cliente = cliente;
        this.execucaoRepository = execucaoRepository;
        this.itemRepository = itemRepository;
        this.estoqueGeralRepository = estoqueGeralRepository;
        this.inventarioService = inventarioService;
        this.jdbc = jdbc;
        this.leitura = new TransactionTemplate(transacoes);
        this.leitura.setReadOnly(true);
        this.escrita = new TransactionTemplate(transacoes);
        this.tamanhoLote = Math.max(1, Math.min(tamanhoLote, 100));
    }

    /** Ao fechar o inventário: falha da IA não desfaz o fechamento, só fica registrada no resumo. */
    public void diagnosticarAoFechar(Long execucaoId) {
        try {
            diagnosticar(execucaoId);
        } catch (DiagnosticoIaException e) {
            log.warn("Diagnóstico do inventário {} falhou ao fechar: {}", execucaoId, e.getMessage());
            gravarResumo(execucaoId, "Diagnóstico falhou: " + e.getMessage() + " Use \"Refazer diagnóstico\".");
        }
    }

    /**
     * {@code POST /estoque/inventario/{id}/diagnostico}. Só com o inventário FECHADO.
     *
     * @throws DiagnosticoIaException (502) se a API falhar ou responder fora do formato duas vezes
     */
    public void diagnosticar(Long execucaoId) {
        List<ItemParaDiagnostico> contexto = leitura.execute(status -> montarContexto(execucaoId));
        if (!cliente.disponivel()) {
            gravarResumo(execucaoId, SEM_CHAVE);
            return;
        }
        if (contexto == null || contexto.isEmpty()) {
            gravarResumo(execucaoId, "Nenhuma falta ou divergência para diagnosticar.");
            return;
        }

        Map<String, RespostaDiagnosticoIa.Item> porImei = new HashMap<>();
        List<String> resumos = new ArrayList<>();
        for (int i = 0; i < contexto.size(); i += tamanhoLote) {
            List<ItemParaDiagnostico> lote = contexto.subList(i, Math.min(i + tamanhoLote, contexto.size()));
            RespostaDiagnosticoIa resposta = chamarComNovaTentativa(lote);
            Set<String> pedidos = new HashSet<>();
            lote.forEach(item -> pedidos.add(item.imei()));
            for (RespostaDiagnosticoIa.Item item : resposta.itens()) {
                // IMEI que não veio no pedido é descartado.
                if (item != null && item.imei() != null && pedidos.contains(item.imei().trim())) {
                    porImei.put(item.imei().trim(), item);
                }
            }
            if (resposta.resumo() != null && !resposta.resumo().isBlank()) {
                resumos.add(resposta.resumo().trim());
            }
        }

        StringBuilder resumo = new StringBuilder();
        for (int i = 0; i < resumos.size(); i++) {
            if (!resumo.isEmpty()) resumo.append("\n\n");
            resumo.append(resumos.size() == 1 ? "" : "Lote " + (i + 1) + ": ").append(resumos.get(i));
        }
        escrita.executeWithoutResult(status -> gravarDiagnostico(execucaoId, porImei, resumo.toString()));
    }

    private RespostaDiagnosticoIa chamarComNovaTentativa(List<ItemParaDiagnostico> lote) {
        RespostaIaInvalidaException ultima = null;
        for (int tentativa = 1; tentativa <= 2; tentativa++) {
            try {
                RespostaDiagnosticoIa resposta = cliente.diagnosticar(lote);
                validar(resposta);
                return resposta;
            } catch (RespostaIaInvalidaException e) {
                ultima = e;
                log.warn("Resposta inválida da IA (tentativa {}): {}", tentativa, e.getMessage());
            }
        }
        throw new DiagnosticoIaException("A IA devolveu uma resposta inválida duas vezes: " + ultima.getMessage(), ultima);
    }

    private static void validar(RespostaDiagnosticoIa resposta) {
        if (resposta == null || resposta.itens() == null) {
            throw new RespostaIaInvalidaException("A resposta não trouxe a lista de itens.");
        }
        for (RespostaDiagnosticoIa.Item item : resposta.itens()) {
            if (item == null || item.imei() == null || item.imei().isBlank() || item.causaProvavel() == null
                    || item.confianca() == null) {
                throw new RespostaIaInvalidaException("Item sem IMEI, causa provável ou confiança.");
            }
        }
    }

    private List<ItemParaDiagnostico> montarContexto(Long execucaoId) {
        InventarioExecucao execucao = execucaoRepository.findById(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inventário"));
        if (execucao.getStatus() != StatusInventario.FECHADO) {
            throw new NegocioException("O diagnóstico roda com o inventário fechado.");
        }
        List<InventarioItem> itens = itemRepository.findByExecucaoIdAndResultadoIn(execucaoId, ResultadoConciliacaoInventario.DIVERGENTES);
        if (itens.isEmpty()) return List.of();

        List<String> seriais = itens.stream().map(InventarioItem::getSerialNormalizado).toList();
        Map<String, List<String>> outrosCentros = inventarioService.outrosCentros(execucaoId, seriais);
        Map<String, EstoqueGeral> estoque = estoquePorSerial(seriais);
        Map<String, ItemParaDiagnostico.UltimaVenda> vendas = ultimasVendas(seriais);
        String filialInventariada = execucao.getFilial() == null ? null : execucao.getFilial().getNome();

        List<ItemParaDiagnostico> contexto = new ArrayList<>();
        for (InventarioItem item : itens) {
            String serial = item.getSerialNormalizado();
            EstoqueGeral unidade = estoque.get(serial);
            contexto.add(new ItemParaDiagnostico(
                    serial,
                    item.getResultado().name(),
                    item.getObservacao(),
                    item.getMaterialSap(),
                    item.getDenominacaoSap(),
                    item.isEsperadoIq09(),
                    item.getStatusSap(),
                    item.getStatusUsuarioSap(),
                    item.getModificadoEmSap(),
                    item.getModificadoPorSap(),
                    unidade == null ? item.getStatusSistema() : nome(unidade.getStatus()),
                    unidade == null ? item.getFilialSistema() : unidade.getFilialEstoque(),
                    unidade == null ? null : unidade.getDataCompra(),
                    unidade == null ? null : unidade.getFilialCompra(),
                    outrosCentros.getOrDefault(serial, List.of()),
                    item.bipado(),
                    item.getJustificativa(),
                    vendas.get(serial)));
        }
        log.debug("Diagnóstico do inventário {} ({}): {} itens", execucaoId, filialInventariada, contexto.size());
        return contexto;
    }

    private Map<String, EstoqueGeral> estoquePorSerial(List<String> seriais) {
        Map<String, EstoqueGeral> porSerial = new HashMap<>();
        for (int i = 0; i < seriais.size(); i += LOTE_CONSULTA) {
            for (EstoqueGeral unidade : estoqueGeralRepository.findBySerialIn(seriais.subList(i, Math.min(i + LOTE_CONSULTA, seriais.size())))) {
                porSerial.put(SerialInventario.normalizar(unidade.getSerial()), unidade);
            }
        }
        return porSerial;
    }

    /** Última venda com o serial: só número, data e status (nenhum dado do cliente sai daqui). */
    private Map<String, ItemParaDiagnostico.UltimaVenda> ultimasVendas(List<String> seriais) {
        Map<String, ItemParaDiagnostico.UltimaVenda> porSerial = new HashMap<>();
        for (int i = 0; i < seriais.size(); i += LOTE_CONSULTA) {
            jdbc.query("""
                    SELECT DISTINCT ON (upper(trim(i.serial_imei)))
                           upper(trim(i.serial_imei)) AS serial, v.numero_venda, v.status,
                           coalesce(v.finalizado_em, v.criado_em) AS data
                    FROM itens_venda i
                    JOIN vendas v ON v.id = i.venda_id
                    WHERE upper(trim(i.serial_imei)) IN (:seriais)
                    ORDER BY upper(trim(i.serial_imei)), v.id DESC
                    """, new MapSqlParameterSource("seriais", seriais.subList(i, Math.min(i + LOTE_CONSULTA, seriais.size()))), rs -> {
                Timestamp data = rs.getTimestamp("data");
                long numero = rs.getLong("numero_venda");
                porSerial.put(rs.getString("serial"), new ItemParaDiagnostico.UltimaVenda(
                        rs.wasNull() ? null : numero,
                        data == null ? null : data.toLocalDateTime().toLocalDate(),
                        rs.getString("status")));
            });
        }
        return porSerial;
    }

    private void gravarDiagnostico(Long execucaoId, Map<String, RespostaDiagnosticoIa.Item> porImei, String resumo) {
        Instant agora = Instant.now();
        List<InventarioItem> itens = itemRepository.findByExecucaoIdAndResultadoIn(execucaoId, ResultadoConciliacaoInventario.DIVERGENTES);
        for (InventarioItem item : itens) {
            RespostaDiagnosticoIa.Item diagnostico = porImei.get(item.getSerialNormalizado());
            if (diagnostico == null) continue;
            item.setCausaProvavel(diagnostico.causaProvavel().name());
            item.setConfiancaIa(diagnostico.confianca().name());
            String acao = diagnostico.acaoSugerida() == null ? null : diagnostico.acaoSugerida().trim();
            item.setAcaoSugerida(acao == null ? null : (acao.length() > 500 ? acao.substring(0, 500) : acao));
            item.setDiagnosticadoEm(agora);
        }
        itemRepository.saveAll(itens);
        InventarioExecucao execucao = execucaoRepository.findById(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inventário"));
        execucao.setResumoIa(resumo);
        execucao.setDiagnosticadoEm(agora);
        execucaoRepository.save(execucao);
    }

    private void gravarResumo(Long execucaoId, String resumo) {
        escrita.executeWithoutResult(status -> execucaoRepository.findById(execucaoId).ifPresent(execucao -> {
            execucao.setResumoIa(resumo);
            execucaoRepository.save(execucao);
        }));
    }

    private static String nome(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }
}
