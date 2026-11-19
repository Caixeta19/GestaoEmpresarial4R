package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.response.ImportacaoMb52ResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.InventarioSkuResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.PaginaSkuResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.ResumoMb52DTO;
import com.vivo4redes.syscor.estoque.enums.OrigemSku;
import com.vivo4redes.syscor.estoque.enums.ResultadoSku;
import com.vivo4redes.syscor.estoque.model.InventarioExecucao;
import com.vivo4redes.syscor.estoque.model.InventarioSku;
import com.vivo4redes.syscor.estoque.repository.InventarioExecucaoRepository;
import com.vivo4redes.syscor.estoque.repository.InventarioItemRepository;
import com.vivo4redes.syscor.estoque.repository.InventarioSkuRepository;
import com.vivo4redes.syscor.estoque.repository.TabelaPrecoExecucaoRepository;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.util.TextoUtil;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Conferência por SKU do inventário (RN.82): MB52 da filial x IQ09 x estoque do sistema x contagem, por quantidade.
 * <p>
 * Informativa: não altera o cruzamento por serial ({@link InventarioClassificador}) nem as regras de fechamento.
 * O SKU de cada item é resolvido uma vez ({@link #resolverSkus}) e gravado no item; os totais por SKU saem de uma
 * agregação SQL (sem N+1). Cada bipagem recalcula só o SKU do item lido, com a linha travada (FOR UPDATE) para que
 * bipagens simultâneas do mesmo SKU não percam contagem.
 */
@Service
@RequiredArgsConstructor
public class InventarioSkuService {

    private static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DATA_HORA_CSV = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(FUSO);
    private static final int TAMANHO_MAXIMO_PAGINA = 500;
    private static final Set<String> UNIDADES_DE_CONTAGEM = Set.of("UN", "UNI", "UNID", "UND", "PC", "PÇ", "PEC", "PECA", "PÇA", "PCE", "ST");

    /** Totais por SKU a partir dos itens: IQ09 (esperados), sistema (acessório soma a quantidade da linha) e bipagens. */
    private static final String SQL_AGREGADO = """
            SELECT i.sku_normalizado AS sku,
                   count(*) FILTER (WHERE i.esperado_iq09) AS iq09,
                   coalesce(sum(CASE WHEN NOT i.esperado_sistema THEN 0
                                     WHEN i.sku_origem = 'ACESSORIO' THEN coalesce(e.quantidade, 1)
                                     ELSE 1 END), 0) AS sistema,
                   count(*) FILTER (WHERE i.bipado_em IS NOT NULL) AS bipada,
                   count(*) FILTER (WHERE i.sku_origem = 'TABELA_PRECO' AND (i.esperado_sistema OR i.bipado_em IS NOT NULL)) AS estimadas,
                   coalesce(bool_or(i.sku_origem = 'ACESSORIO'), FALSE) AS acessorio,
                   coalesce(bool_or(i.sku_origem = 'TABELA_PRECO'), FALSE) AS tabela,
                   max(i.material_sap) AS material,
                   max(coalesce(nullif(i.denominacao_sap, ''), e.modelo_comercial)) AS denominacao
            FROM tb_inventario_item i
            LEFT JOIN tb_estoque_geral e ON e.id = i.item_estoque_id
            WHERE i.execucao_id = :execucao
            """;

    private final RelatorioMb52Parser parser;
    private final InventarioExecucaoRepository execucaoRepository;
    private final InventarioItemRepository itemRepository;
    private final InventarioSkuRepository skuRepository;
    private final TabelaPrecoExecucaoRepository tabelaPrecoRepository;
    private final UsuarioService usuarioService;
    private final NamedParameterJdbcTemplate jdbc;

    // ======================================================================
    // Importar MB52
    // ======================================================================

    /** {@code POST /{id}/mb52}: só com a execução ABERTO/EM_CONTAGEM; reenviar substitui a anterior. */
    @Transactional
    public ImportacaoMb52ResponseDTO importar(Long execucaoId, MultipartFile arquivo) {
        InventarioExecucao execucao = execucaoRepository.buscarParaFechamento(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inventário"));
        exigirEmAndamento(execucao);
        return importar(execucao, arquivo);
    }

    /**
     * Importa na execução já travada (também usado na abertura). Filtra pelo centro/depósito da filial (sem depósito
     * cadastrado, o centro todo), soma material repetido, preserva as contagens manuais e recalcula tudo.
     */
    @Transactional
    public ImportacaoMb52ResponseDTO importar(InventarioExecucao execucao, MultipartFile arquivo) {
        LinhaMb52DTO.Leitura leitura = parser.parsear(arquivo);
        if (leitura.linhas().isEmpty()) {
            throw new NegocioException("A MB52 não tem nenhuma linha de dados.");
        }
        Filial filial = execucao.getFilial();
        String centro = filial.getCentroSap() == null ? "" : filial.getCentroSap().trim().toUpperCase(Locale.ROOT);
        String deposito = filial.getDepositoSap() == null ? "" : filial.getDepositoSap().trim().toUpperCase(Locale.ROOT);

        Map<String, AgregadoMb52> porSku = new LinkedHashMap<>();
        int daFilial = 0;
        int outroCentro = 0;
        int ignoradas = leitura.ignoradas();
        for (LinhaMb52DTO linha : leitura.linhas()) {
            boolean daLoja = centro.equals(linha.centro()) && (deposito.isEmpty() || deposito.equals(linha.deposito()));
            if (!daLoja) {
                outroCentro++;
                continue;
            }
            String sku = SkuInventario.normalizar(linha.material());
            if (sku.isEmpty()) {
                ignoradas++;
                continue;
            }
            daFilial++;
            porSku.computeIfAbsent(sku, s -> new AgregadoMb52(linha.material())).somar(linha);
        }

        itemRepository.flush();
        resolverSkus(execucao.getId());

        // Reaproveita as linhas existentes (preserva a contagem manual) e zera o que vinha da MB52 anterior.
        Map<String, InventarioSku> linhas = carregarLinhas(execucao);
        for (InventarioSku s : linhas.values()) {
            s.setTemLinhaMb52(false);
            s.setQtdMb52Livre(BigDecimal.ZERO);
            s.setQtdMb52Qualidade(BigDecimal.ZERO);
            s.setQtdMb52Bloqueado(BigDecimal.ZERO);
            s.setQtdMb52Transito(BigDecimal.ZERO);
            s.setValorUnitarioSap(null);
        }
        porSku.forEach((sku, mb52) -> mb52.aplicar(linhas.computeIfAbsent(sku, k -> novaLinha(execucao, k, mb52.material))));
        recalcular(execucao, linhas);

        Usuario usuario = usuarioService.usuarioLogado();
        Instant agora = Instant.now();
        execucao.setMb52NomeArquivo(limitar(arquivo.getOriginalFilename(), 255));
        execucao.setMb52ImportadaEm(agora);
        execucao.setMb52ImportadaPor(usuario);
        execucao.setMb52TotalLinhas(leitura.total());
        execucao.setMb52LinhasDaFilial(daFilial);
        execucao.setMb52LinhasOutroCentro(outroCentro);
        execucao.setMb52LinhasIgnoradas(ignoradas);
        execucaoRepository.save(execucao);
        jdbc.update("""
                INSERT INTO tb_inventario_mb52_importacao
                    (execucao_id, nome_arquivo, importada_em, importada_por_id, total_linhas, linhas_da_filial)
                VALUES (:execucao, :arquivo, :em, :usuario, :linhas, :daFilial)
                """, new MapSqlParameterSource()
                .addValue("execucao", execucao.getId())
                .addValue("arquivo", execucao.getMb52NomeArquivo())
                .addValue("em", LocalDateTime.ofInstant(agora, FUSO))
                .addValue("usuario", usuario.getId())
                .addValue("linhas", leitura.total())
                .addValue("daFilial", daFilial));

        Map<String, Long> totais = totaisPorResultado(execucao.getId());
        long comDiferenca = totais.entrySet().stream()
                .filter(e -> !ResultadoSku.CONFERIDO.name().equals(e.getKey()))
                .mapToLong(Map.Entry::getValue).sum();
        return new ImportacaoMb52ResponseDTO(leitura.total(), daFilial, outroCentro, ignoradas, comDiferenca,
                resumo(execucao, true));
    }

    /** Quantidades de um material da MB52 (linhas repetidas, ex.: lotes, somadas). */
    private static final class AgregadoMb52 {
        final String material;
        String denominacao = "";
        String unidade = "";
        BigDecimal livre = BigDecimal.ZERO;
        BigDecimal qualidade = BigDecimal.ZERO;
        BigDecimal bloqueado = BigDecimal.ZERO;
        BigDecimal transito = BigDecimal.ZERO;
        BigDecimal valorLivre;

        AgregadoMb52(String material) {
            this.material = material;
        }

        void somar(LinhaMb52DTO l) {
            if (denominacao.isBlank()) denominacao = l.denominacao();
            if (unidade.isBlank()) unidade = l.unidade();
            livre = livre.add(l.livre());
            qualidade = qualidade.add(l.qualidade());
            bloqueado = bloqueado.add(l.bloqueado());
            transito = transito.add(l.transito());
            if (l.valorLivre() != null) valorLivre = valorLivre == null ? l.valorLivre() : valorLivre.add(l.valorLivre());
        }

        void aplicar(InventarioSku s) {
            s.setTemLinhaMb52(true);
            s.setMaterialSap(limitar(material, 40));
            if (!denominacao.isBlank()) s.setDenominacao(limitar(denominacao, 200));
            if (!unidade.isBlank()) s.setUnidade(limitar(unidade, 10));
            s.setQtdMb52Livre(livre);
            s.setQtdMb52Qualidade(qualidade);
            s.setQtdMb52Bloqueado(bloqueado);
            s.setQtdMb52Transito(transito);
            s.setValorUnitarioSap(valorLivre == null || livre.signum() <= 0 ? null
                    : valorLivre.divide(livre, 4, RoundingMode.HALF_UP));
        }
    }

    // ======================================================================
    // SKU de cada item
    // ======================================================================

    /** SKU resolvido de um item: material normalizado (nulo = não identificado) e a origem. */
    record SkuResolvido(String sku, OrigemSku origem) {
    }

    /**
     * Ordem: acessório ({@code codigo_acessorio}) → material da IQ09 pelo serial → tabela de preços vigente (modelo
     * comercial + cor com um único Cod_Sap) → não identificado. Nunca chuta.
     *
     * @param tabela chave do nome comercial → [chave da cor, SKU] da tabela de preços vigente
     */
    static SkuResolvido resolver(String materialIq09, String codigoAcessorio, String modelo, String cor,
                                 Map<String, List<String[]>> tabela) {
        String acessorio = SkuInventario.normalizar(codigoAcessorio);
        if (!acessorio.isEmpty()) return new SkuResolvido(acessorio, OrigemSku.ACESSORIO);
        String iq09 = SkuInventario.normalizar(materialIq09);
        if (!iq09.isEmpty()) return new SkuResolvido(iq09, OrigemSku.IQ09);
        if (modelo != null && !modelo.isBlank()) {
            List<String[]> candidatos = tabela.getOrDefault(TextoUtil.chave(modelo), List.of());
            String chaveCor = cor == null ? "" : TextoUtil.chave(cor);
            Set<String> skus = new HashSet<>();
            for (String[] c : candidatos) {
                if (chaveCor.isEmpty() || chaveCor.equals(c[0])) skus.add(c[1]);
            }
            if (skus.size() == 1) return new SkuResolvido(skus.iterator().next(), OrigemSku.TABELA_PRECO);
        }
        return new SkuResolvido(null, OrigemSku.NAO_IDENTIFICADO);
    }

    /** Resolve o SKU de todos os itens da execução (uma consulta para os itens, uma para a tabela de preços). */
    private void resolverSkus(Long execucaoId) {
        List<Object[]> atualizacoes = new ArrayList<>();
        Map<String, List<String[]>> tabela = tabelaDePrecos(null);
        jdbc.query("""
                SELECT i.id, i.material_sap, e.codigo_acessorio, e.modelo_comercial, e.cor
                FROM tb_inventario_item i LEFT JOIN tb_estoque_geral e ON e.id = i.item_estoque_id
                WHERE i.execucao_id = :execucao
                """, Map.of("execucao", execucaoId), rs -> {
            SkuResolvido r = resolver(rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), tabela);
            atualizacoes.add(new Object[]{r.sku(), r.origem().name(), rs.getLong(1)});
        });
        jdbc.getJdbcTemplate().batchUpdate("UPDATE tb_inventario_item SET sku_normalizado = ?, sku_origem = ? WHERE id = ?",
                atualizacoes);
    }

    /** Resolve o SKU de um item (bipagem fora da lista depois da MB52). Devolve o SKU (nulo = não identificado). */
    private String resolverSku(Long itemId) {
        List<Map<String, Object>> linha = jdbc.queryForList("""
                SELECT i.material_sap, e.codigo_acessorio, e.modelo_comercial, e.cor
                FROM tb_inventario_item i LEFT JOIN tb_estoque_geral e ON e.id = i.item_estoque_id
                WHERE i.id = :id
                """, Map.of("id", itemId));
        if (linha.isEmpty()) return null;
        Map<String, Object> l = linha.get(0);
        String modelo = (String) l.get("modelo_comercial");
        Map<String, List<String[]>> tabela = modelo == null || modelo.isBlank() ? Map.of() : tabelaDePrecos(TextoUtil.chave(modelo));
        SkuResolvido r = resolver((String) l.get("material_sap"), (String) l.get("codigo_acessorio"), modelo,
                (String) l.get("cor"), tabela);
        jdbc.update("UPDATE tb_inventario_item SET sku_normalizado = :sku, sku_origem = :origem WHERE id = :id",
                new MapSqlParameterSource().addValue("sku", r.sku()).addValue("origem", r.origem().name()).addValue("id", itemId));
        return r.sku();
    }

    /** Produtos da tabela de preços vigente (aba PRODUTOS): chave do nome → [chave da cor, SKU]. {@code chaveNome} nulo = todos. */
    private Map<String, List<String[]>> tabelaDePrecos(String chaveNome) {
        Optional<Long> vigente = tabelaPrecoRepository.findTopByOrderByIdDesc().map(t -> t.getId());
        if (vigente.isEmpty()) return Map.of();
        Map<String, List<String[]>> porNome = new HashMap<>();
        MapSqlParameterSource p = new MapSqlParameterSource("execucao", vigente.get()).addValue("chave", chaveNome);
        jdbc.query("SELECT chave_nome, cod_sap, cor FROM tb_produto_vivo WHERE execucao_id = :execucao"
                + (chaveNome == null ? "" : " AND chave_nome = :chave"), p, rs -> {
            String sku = SkuInventario.normalizar(rs.getString("cod_sap"));
            if (sku.isEmpty()) return;
            String cor = rs.getString("cor") == null ? "" : TextoUtil.chave(rs.getString("cor"));
            porNome.computeIfAbsent(rs.getString("chave_nome"), k -> new ArrayList<>()).add(new String[]{cor, sku});
        });
        return porNome;
    }

    // ======================================================================
    // Recalcular
    // ======================================================================

    /** Recalcula todas as linhas (importação e fechamento). */
    @Transactional
    public void recalcular(InventarioExecucao execucao) {
        itemRepository.flush();
        recalcular(execucao, carregarLinhas(execucao));
    }

    private void recalcular(InventarioExecucao execucao, Map<String, InventarioSku> linhas) {
        linhas.computeIfAbsent(null, k -> novaLinha(execucao, null, null));
        Set<String> comItens = new HashSet<>();
        jdbc.query(SQL_AGREGADO + " GROUP BY i.sku_normalizado", Map.of("execucao", execucao.getId()), rs -> {
            String sku = rs.getString("sku");
            String material = rs.getString("material");
            comItens.add(sku);
            aplicarAgregado(linhas.computeIfAbsent(sku, k -> novaLinha(execucao, k, material)), rs);
        });
        for (Map.Entry<String, InventarioSku> e : linhas.entrySet()) {
            if (!comItens.contains(e.getKey())) zerarAgregado(e.getValue());
            InventarioSkuClassificador.classificar(e.getValue());
        }
        skuRepository.saveAll(linhas.values());
        skuRepository.flush();
    }

    /**
     * Depois de bipar/desfazer: recalcula só o SKU do item. Item novo (sobra) ganha o SKU aqui. Sem MB52 não faz nada.
     */
    @Transactional
    public void aposBipagem(InventarioExecucao execucao, Long itemId, String skuConhecido, boolean skuResolvido) {
        if (!execucao.temMb52()) return;
        itemRepository.flush();
        String sku = skuResolvido ? skuConhecido : resolverSku(itemId);
        recalcularLinha(execucao, travarOuCriar(execucao, sku));
    }

    private InventarioSku travarOuCriar(InventarioExecucao execucao, String sku) {
        Optional<InventarioSku> linha = travar(execucao.getId(), sku);
        if (linha.isPresent()) return linha.get();
        // Duas bipagens do mesmo SKU novo ao mesmo tempo: só uma insere, a outra espera a trava abaixo.
        jdbc.update("""
                INSERT INTO tb_inventario_sku (execucao_id, material_normalizado, material_sap, resultado)
                VALUES (:execucao, :sku, :sku, 'CONFERIDO')
                ON CONFLICT DO NOTHING
                """, new MapSqlParameterSource("execucao", execucao.getId()).addValue("sku", sku));
        return travar(execucao.getId(), sku).orElseThrow(() -> new IllegalStateException("Linha de SKU não criada: " + sku));
    }

    private Optional<InventarioSku> travar(Long execucaoId, String sku) {
        return sku == null ? skuRepository.travarNaoIdentificado(execucaoId) : skuRepository.travar(execucaoId, sku);
    }

    private void recalcularLinha(InventarioExecucao execucao, InventarioSku linha) {
        String sql = SQL_AGREGADO + (linha.naoIdentificado()
                ? " AND i.sku_normalizado IS NULL AND i.sku_origem IS NOT NULL"
                : " AND i.sku_normalizado = :sku") + " GROUP BY i.sku_normalizado";
        boolean[] achou = {false};
        jdbc.query(sql, new MapSqlParameterSource("execucao", execucao.getId()).addValue("sku", linha.getMaterialNormalizado()), rs -> {
            achou[0] = true;
            aplicarAgregado(linha, rs);
        });
        if (!achou[0]) zerarAgregado(linha);
        InventarioSkuClassificador.classificar(linha);
        skuRepository.save(linha);
    }

    private static void aplicarAgregado(InventarioSku s, ResultSet rs) throws SQLException {
        s.setQtdIq09(rs.getInt("iq09"));
        s.setQtdSistema(rs.getInt("sistema"));
        s.setQtdBipada(rs.getInt("bipada"));
        s.setUnidadesEstimadas(rs.getInt("estimadas"));
        if (s.naoIdentificado()) {
            s.setOrigemSku(null);
        } else {
            s.setOrigemSku(rs.getBoolean("acessorio") ? OrigemSku.ACESSORIO
                    : rs.getBoolean("tabela") ? OrigemSku.TABELA_PRECO : OrigemSku.IQ09);
        }
        if (s.getDenominacao() == null || s.getDenominacao().isBlank()) {
            s.setDenominacao(limitar(rs.getString("denominacao"), 200));
        }
    }

    private static void zerarAgregado(InventarioSku s) {
        s.setQtdIq09(0);
        s.setQtdSistema(0);
        s.setQtdBipada(0);
        s.setUnidadesEstimadas(0);
        if (!s.isTemLinhaMb52()) s.setOrigemSku(null);
    }

    private Map<String, InventarioSku> carregarLinhas(InventarioExecucao execucao) {
        Map<String, InventarioSku> linhas = new HashMap<>();
        for (InventarioSku s : skuRepository.findByExecucaoId(execucao.getId())) {
            linhas.put(s.getMaterialNormalizado(), s);
        }
        return linhas;
    }

    private static InventarioSku novaLinha(InventarioExecucao execucao, String sku, String material) {
        return InventarioSku.builder()
                .execucao(execucao)
                .materialNormalizado(sku)
                .materialSap(sku == null ? null : limitar(material == null || material.isBlank() ? sku : material, 40))
                .build();
    }

    // ======================================================================
    // Contagem manual
    // ======================================================================

    /**
     * Contagem manual de um SKU (acessório com quantidade N: a bipagem do serial conta 1). Não mexe na bipagem.
     * {@code quantidade} nula remove a contagem manual.
     */
    @Transactional
    public InventarioSkuResponseDTO contar(Long execucaoId, Long skuId, BigDecimal quantidade, String justificativa) {
        InventarioExecucao execucao = execucaoRepository.buscarParaBipagem(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inventário"));
        exigirEmAndamento(execucao);
        InventarioSku linha = skuRepository.travarPorId(execucaoId, skuId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("SKU do inventário"));
        if (linha.naoIdentificado()) {
            throw new NegocioException("Itens sem SKU identificado não recebem contagem manual: bipe os seriais.");
        }
        if (quantidade == null) {
            linha.setQtdContagemManual(null);
            linha.setContagemJustificativa(null);
            linha.setContadoPor(null);
            linha.setContadoEm(null);
        } else {
            if (quantidade.signum() < 0) {
                throw new NegocioException("A quantidade contada não pode ser negativa.");
            }
            String texto = justificativa == null ? "" : justificativa.trim();
            if (texto.isEmpty()) {
                throw new NegocioException("Informe a justificativa da contagem manual.");
            }
            String unidade = linha.getUnidade() == null ? "" : linha.getUnidade().trim().toUpperCase(Locale.ROOT);
            if ((unidade.isEmpty() || UNIDADES_DE_CONTAGEM.contains(unidade)) && quantidade.stripTrailingZeros().scale() > 0) {
                throw new NegocioException("Quantidade fracionada não vale para " + (unidade.isEmpty() ? "unidades" : unidade) + ".");
            }
            linha.setQtdContagemManual(quantidade);
            linha.setContagemJustificativa(limitar(texto, 300));
            linha.setContadoPor(usuarioService.usuarioLogado());
            linha.setContadoEm(Instant.now());
        }
        itemRepository.flush();
        recalcularLinha(execucao, linha);
        return InventarioSkuResponseDTO.from(linha);
    }

    // ======================================================================
    // Consultas e exportação
    // ======================================================================

    @Transactional(readOnly = true)
    public PaginaSkuResponseDTO listar(Long execucaoId, Collection<ResultadoSku> resultados, String busca,
                                       boolean somenteDiferenca, String ordenacao, int pagina, int tamanho) {
        InventarioExecucao execucao = execucaoRepository.findById(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Inventário"));
        Collection<ResultadoSku> filtro = resultados == null || resultados.isEmpty() ? EnumSet.allOf(ResultadoSku.class) : resultados;
        String termo = busca == null || busca.isBlank() ? null : "%" + busca.trim().toUpperCase(Locale.ROOT) + "%";
        int tamanhoPagina = Math.min(Math.max(tamanho, 1), TAMANHO_MAXIMO_PAGINA);
        Page<InventarioSku> page = skuRepository.buscar(execucaoId, filtro, somenteDiferenca, termo,
                ordenacao == null ? "sku" : ordenacao.trim().toLowerCase(Locale.ROOT),
                PageRequest.of(Math.max(pagina, 0), tamanhoPagina));
        return new PaginaSkuResponseDTO(
                page.getContent().stream().map(InventarioSkuResponseDTO::from).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                totaisPorResultado(execucaoId),
                skuRepository.somarValorDiferencas(execucaoId),
                resumo(execucao, false));
    }

    /** Linhas visíveis por resultado (todos os resultados presentes, zerados quando não há). */
    @Transactional(readOnly = true)
    public Map<String, Long> totaisPorResultado(Long execucaoId) {
        Map<String, Long> totais = new LinkedHashMap<>();
        for (ResultadoSku r : ResultadoSku.values()) totais.put(r.name(), 0L);
        for (Object[] linha : skuRepository.contarPorResultado(execucaoId)) {
            totais.put(((ResultadoSku) linha[0]).name(), ((Number) linha[1]).longValue());
        }
        return totais;
    }

    /** MB52 no resumo da execução. {@code comTotais}: uma consulta agrupada a mais (só no detalhe). */
    public ResumoMb52DTO resumo(InventarioExecucao e, boolean comTotais) {
        if (!e.temMb52()) return ResumoMb52DTO.NAO_IMPORTADA;
        return new ResumoMb52DTO(true, e.getMb52NomeArquivo(), e.getMb52ImportadaEm(),
                comTotais && e.getMb52ImportadaPor() != null ? e.getMb52ImportadaPor().getNome() : null,
                e.getMb52TotalLinhas(), e.getMb52LinhasDaFilial(), e.getMb52LinhasOutroCentro(), e.getMb52LinhasIgnoradas(),
                comTotais ? totaisPorResultado(e.getId()) : null);
    }

    /** Fechamento: último recálculo (fica congelado) e totais por resultado gravados na execução. */
    @Transactional
    public void aoFechar(InventarioExecucao execucao) {
        if (!execucao.temMb52()) return;
        recalcular(execucao);
        Map<String, Long> t = totaisPorResultado(execucao.getId());
        execucao.setTotalSkuConferido(t.get(ResultadoSku.CONFERIDO.name()).intValue());
        execucao.setTotalSkuMb52DifereIq09(t.get(ResultadoSku.MB52_DIFERE_IQ09.name()).intValue());
        execucao.setTotalSkuSapAcimaSistema(t.get(ResultadoSku.SAP_ACIMA_SISTEMA.name()).intValue());
        execucao.setTotalSkuSapAbaixoSistema(t.get(ResultadoSku.SAP_ABAIXO_SISTEMA.name()).intValue());
        execucao.setTotalSkuSoNoSap(t.get(ResultadoSku.SO_NO_SAP.name()).intValue());
        execucao.setTotalSkuSoNoSistema(t.get(ResultadoSku.SO_NO_SISTEMA.name()).intValue());
        execucao.setTotalSkuNaoIdentificado(t.get(ResultadoSku.SKU_NAO_IDENTIFICADO.name()).intValue());
        execucao.setTotalSkuContagemDivergente(t.get(ResultadoSku.CONTAGEM_DIVERGENTE.name()).intValue());
    }

    /** CSV (;, UTF-8 com BOM) com as colunas da tela; números com vírgula decimal (Excel pt-BR). */
    @Transactional(readOnly = true)
    public byte[] exportarCsv(Long execucaoId) {
        execucaoRepository.findById(execucaoId).orElseThrow(() -> new RecursoNaoEncontradoException("Inventário"));
        StringBuilder csv = new StringBuilder("﻿");
        csv.append(String.join(";", "SKU", "Material SAP", "Descrição", "Unidade", "MB52 (utilização livre)", "IQ09",
                "Sistema", "Bipado", "Contagem manual", "Contagem", "Diferença SAP - sistema", "Diferença SAP - IQ09",
                "Diferença contagem - SAP", "Em controle de qualidade", "Bloqueado", "Em trânsito", "Valor unitário SAP",
                "Valor da diferença (estimativa)", "Origem do SKU", "Resultado", "Observação", "Justificativa da contagem",
                "Contado por", "Contado em")).append('\n');
        for (InventarioSku s : skuRepository.listarParaExportacao(execucaoId)) {
            csv.append(String.join(";",
                    texto(s.naoIdentificado() ? "NÃO IDENTIFICADO" : s.getMaterialNormalizado()), texto(s.getMaterialSap()),
                    texto(s.getDenominacao()), texto(s.getUnidade()), numero(s.getQtdMb52Livre()),
                    String.valueOf(s.getQtdIq09()), String.valueOf(s.getQtdSistema()), String.valueOf(s.getQtdBipada()),
                    numero(s.getQtdContagemManual()), s.contou() ? numero(s.contagemEfetiva()) : "",
                    numero(s.getDifSapSistema()), numero(s.getDifSapIq09()), numero(s.getDifContagem()),
                    numero(s.getQtdMb52Qualidade()), numero(s.getQtdMb52Bloqueado()), numero(s.getQtdMb52Transito()),
                    numero(s.getValorUnitarioSap()), numero(s.getValorDiferenca()),
                    s.getOrigemSku() == null ? "" : s.getOrigemSku().name(), s.getResultado().name(),
                    texto(s.getObservacao()), texto(s.getContagemJustificativa()),
                    texto(s.getContadoPor() == null ? null : s.getContadoPor().getNome()),
                    s.getContadoEm() == null ? "" : DATA_HORA_CSV.format(s.getContadoEm()))).append('\n');
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static String numero(BigDecimal valor) {
        return valor == null ? "" : valor.stripTrailingZeros().toPlainString().replace('.', ',');
    }

    /** Sem ; nem quebra de linha; material longo só de dígitos vai como texto para o Excel não arredondar. */
    private static String texto(String valor) {
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
}
