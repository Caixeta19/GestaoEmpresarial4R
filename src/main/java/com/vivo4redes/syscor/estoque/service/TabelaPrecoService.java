package com.vivo4redes.syscor.estoque.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.estoque.dto.response.ImportacaoTabelaPrecoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.PlanoVivoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.PrecoItemResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.TabelaPrecoResumoResponseDTO;
import com.vivo4redes.syscor.estoque.dto.response.TabelaVigenteResponseDTO;
import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.model.PrecoItem;
import com.vivo4redes.syscor.estoque.model.TabelaPrecoExecucao;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.estoque.repository.PlanoVivoRepository;
import com.vivo4redes.syscor.estoque.repository.PrecoItemRepository;
import com.vivo4redes.syscor.estoque.repository.ProdutoVivoRepository;
import com.vivo4redes.syscor.estoque.repository.TabelaPrecoExecucaoRepository;
import com.vivo4redes.syscor.estoque.service.PrecoVigenteService.IndicesReferencia;
import com.vivo4redes.syscor.estoque.service.PrecoVigenteService.ResultadoReferencia;
import com.vivo4redes.syscor.estoque.service.ResultadoParseTabelaPreco.LinhaPlano;
import com.vivo4redes.syscor.estoque.service.ResultadoParseTabelaPreco.LinhaPreco;
import com.vivo4redes.syscor.estoque.service.ResultadoParseTabelaPreco.LinhaProduto;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.util.TextoUtil;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Importação (upload manual) da tabela de preços da Telefônica.
 * <p>
 * Cada upload cria uma execução; a mais recente é a VIGENTE. Tudo roda numa transação só (regra 11):
 * se algo falhar, nada da execução nova fica gravado e a vigente continua a anterior.
 * <p>
 * Grava preços (tb_preco_item), planos (tb_plano_vivo) e produtos (tb_produto_vivo) da execução e atualiza o
 * preço de referência ({@code valor_venda}) das unidades DISPONIVEL e RESERVADO (regra 7). VENDIDO não muda.
 * {@code tb_produto_preco_plano} deixou de ser gravada.
 */
@Service
@RequiredArgsConstructor
public class TabelaPrecoService {

    private static final Logger log = LoggerFactory.getLogger(TabelaPrecoService.class);
    private static final DateTimeFormatter DD_MM_YYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int TAMANHO_LOTE = 1000;

    private final TabelaPrecoTelefonicaParser parser;
    private final EstoqueGeralRepository estoqueGeralRepository;
    private final TabelaPrecoExecucaoRepository execucaoRepository;
    private final PrecoItemRepository precoItemRepository;
    private final PlanoVivoRepository planoVivoRepository;
    private final ProdutoVivoRepository produtoVivoRepository;
    private final PrecoVigenteService precoVigenteService;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @Transactional
    public ImportacaoTabelaPrecoResponseDTO importar(MultipartFile arquivo) {
        ResultadoParseTabelaPreco lido = parser.parsear(arquivo);

        TabelaPrecoExecucao execucao = execucaoRepository.saveAndFlush(TabelaPrecoExecucao.builder()
                .nomeArquivo(arquivo.getOriginalFilename())
                .vigenciaEm(lido.vigencia())
                .totalLinhasProcessadas(lido.precos().size())
                .totalProdutosAtualizados(0)
                .totalNaoEncontrados(0)
                .build());

        gravarProdutos(execucao.getId(), lido.produtos());
        gravarPlanos(execucao.getId(), lido.planos());
        gravarPrecos(execucao.getId(), lido.precos());

        IndicesReferencia indices = indices(lido);
        List<EstoqueGeral> unidades = estoqueGeralRepository.findByStatusIn(PrecoVigenteService.STATUS_COM_PRECO_VIGENTE);
        ResultadoReferencia referencia = precoVigenteService.aplicarReferencia(unidades, indices);
        int planilhaSemEstoque = contarPlanilhaSemEstoque(lido, indices);

        execucao.setTotalProdutosAtualizados(referencia.unidadesAtualizadas());
        execucao.setTotalNaoEncontrados(planilhaSemEstoque);
        execucaoRepository.save(execucao);

        log.info("Tabela de preços importada (execução {}, vigência {}): {} preços, {} planos, {} produtos, "
                        + "{} unidades com preço, {} modelos do estoque sem preço, {} itens da planilha sem estoque.",
                execucao.getId(), lido.vigencia(), lido.precos().size(), lido.planos().size(), lido.produtos().size(),
                referencia.unidadesAtualizadas(), referencia.modelosSemPreco().size(), planilhaSemEstoque);

        return new ImportacaoTabelaPrecoResponseDTO(
                execucao.getId(), lido.vigencia(), lido.linhasPorAba(),
                lido.precos().size(), lido.planos().size(), lido.produtos().size(),
                referencia.unidadesAtualizadas(), List.copyOf(referencia.modelosSemPreco()), planilhaSemEstoque,
                execucao.getNomeArquivo(),
                lido.vigencia() == null ? null : lido.vigencia().format(DD_MM_YYYY),
                execucao.getCriadoEm(),
                execucao.getTotalLinhasProcessadas(), execucao.getTotalProdutosAtualizados(), execucao.getTotalNaoEncontrados());
    }

    // ------------------------------------------------------------------ gravação em lote

    private void gravarProdutos(Long execucaoId, List<LinhaProduto> produtos) {
        String sql = """
                INSERT INTO tb_produto_vivo (execucao_id, cod_sap, descricao_sap, modelo_dpgc, cor, nome_comercial, chave_nome, categoria)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        emLotes(sql, produtos, p -> new Object[]{execucaoId, truncar(p.codSap(), 60), truncar(p.descricaoSap(), 255),
                truncar(p.modeloDpgc(), 200), truncar(p.cor(), 60), truncar(p.nomeComercial(), 200),
                truncar(TextoUtil.chave(p.nomeComercial()), 200), truncar(p.categoria(), 80)});
    }

    private void gravarPlanos(Long execucaoId, List<LinhaPlano> planos) {
        String sql = """
                INSERT INTO tb_plano_vivo (execucao_id, nome_plano, chave_plano, sistema, cod_sistema, cod_sap, grupo_plano, disponivel_alta)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        emLotes(sql, planos, p -> new Object[]{execucaoId, truncar(p.nomePlano(), 200),
                truncar(TextoUtil.chave(p.nomePlano()), 200), truncar(p.sistema(), 60), truncar(p.codSistema(), 60),
                truncar(p.codSap(), 60), p.grupo().name(), p.disponivelAlta()});
    }

    private void gravarPrecos(Long execucaoId, List<LinhaPreco> precos) {
        String sql = """
                INSERT INTO tb_preco_item (execucao_id, aba, tabela, grupo_plano, nome_comercial, chave_nome, preco, preco_pix, parcelas)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """;
        emLotes(sql, precos, p -> new Object[]{execucaoId, truncar(p.aba(), 60), p.tabela().name(), p.grupo().name(),
                truncar(p.nomeComercial(), 200), truncar(TextoUtil.chave(p.nomeComercial()), 200),
                p.preco(), p.precoPix(), json(p.parcelas())});
    }

    private <T> void emLotes(String sql, List<T> linhas, java.util.function.Function<T, Object[]> parametros) {
        for (int ini = 0; ini < linhas.size(); ini += TAMANHO_LOTE) {
            List<Object[]> lote = linhas.subList(ini, Math.min(ini + TAMANHO_LOTE, linhas.size()))
                    .stream().map(parametros).toList();
            jdbc.batchUpdate(sql, lote);
        }
    }

    private String json(Map<Integer, BigDecimal> parcelas) {
        if (parcelas == null || parcelas.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(parcelas);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar parcelas", e);
        }
    }

    // ------------------------------------------------------------------ referência e contagens

    /** Índices da execução recém-lida (em memória): Cod_Sap -> chave e chave -> preço REGULAR PRE (ou SEM_PLANO). */
    private IndicesReferencia indices(ResultadoParseTabelaPreco lido) {
        Map<String, String> chavePorCodSap = new HashMap<>();
        for (LinhaProduto p : lido.produtos()) {
            chavePorCodSap.putIfAbsent(p.codSap().trim().toUpperCase(Locale.ROOT), TextoUtil.chave(p.nomeComercial()));
        }
        Map<String, BigDecimal> referencia = new HashMap<>();
        for (GrupoPlano grupo : List.of(GrupoPlano.PRE, GrupoPlano.SEM_PLANO)) {
            for (LinhaPreco p : lido.precos()) {
                if (p.tabela() == TabelaPreco.REGULAR && p.grupo() == grupo) {
                    referencia.putIfAbsent(TextoUtil.chave(p.nomeComercial()), p.preco());
                }
            }
        }
        return new IndicesReferencia(chavePorCodSap, referencia);
    }

    /** Itens distintos da planilha (por Nome Comercial) sem nenhuma unidade correspondente no estoque. */
    private int contarPlanilhaSemEstoque(ResultadoParseTabelaPreco lido, IndicesReferencia indices) {
        Set<String> chavesEstoque = new HashSet<>();
        estoqueGeralRepository.modelosDistintos().forEach(m -> chavesEstoque.add(TextoUtil.chave(m)));
        for (String codigo : estoqueGeralRepository.codigosAcessorioDistintos()) {
            String chave = indices.chavePorCodSap().get(codigo.trim().toUpperCase(Locale.ROOT));
            if (chave != null) chavesEstoque.add(chave);
        }
        Set<String> chavesPlanilha = new HashSet<>();
        lido.precos().forEach(p -> chavesPlanilha.add(TextoUtil.chave(p.nomeComercial())));
        chavesPlanilha.removeAll(chavesEstoque);
        return chavesPlanilha.size();
    }

    private static String truncar(String texto, int max) {
        if (texto == null) return null;
        return texto.length() <= max ? texto : texto.substring(0, max);
    }

    // ------------------------------------------------------------------ consultas (API nova)

    /** GET /produtos/precos/vigente. */
    @Transactional(readOnly = true)
    public TabelaVigenteResponseDTO buscarVigente() {
        TabelaPrecoExecucao e = exigirVigente();
        return new TabelaVigenteResponseDTO(e.getId(), e.getVigenciaEm(), e.getCriadoEm(), e.getNomeArquivo(),
                new TabelaVigenteResponseDTO.Totais(precoItemRepository.countByExecucaoId(e.getId()),
                        planoVivoRepository.countByExecucaoId(e.getId()), produtoVivoRepository.countByExecucaoId(e.getId()),
                        e.getTotalProdutosAtualizados(), e.getTotalNaoEncontrados()));
    }

    /** GET /produtos/precos/planos — combo "Plano Ativo / Linha" da execução vigente. */
    @Transactional(readOnly = true)
    public List<PlanoVivoResponseDTO> listarPlanos(String busca, Boolean disponivelAlta) {
        TabelaPrecoExecucao e = exigirVigente();
        return planoVivoRepository.listar(e.getId(), TextoUtil.chave(busca), disponivelAlta).stream()
                .map(PlanoVivoResponseDTO::from)
                .toList();
    }

    /** GET /produtos/precos/item — preço de uma unidade do estoque (422 se sem preço). */
    @Transactional(readOnly = true)
    public PrecoItemResponseDTO precoDoItem(Long estoqueId, String plano, TabelaPreco tabela,
                                            FormaPagamento pagamento, Integer parcelas) {
        EstoqueGeral unidade = estoqueGeralRepository.findById(estoqueId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Item de estoque"));
        return PrecoItemResponseDTO.from(precoVigenteService.calcular(unidade, plano, tabela, pagamento, parcelas));
    }

    // ------------------------------------------------------------------ consultas antigas (deprecadas)
    // Respondem a partir das tabelas novas, no formato antigo, até o syscor-frontend migrar.
    // "Oferta" passa a ser o grupo do plano (PRE, VIVO_V...), sempre da TABELA REGULAR da execução vigente.

    @Deprecated
    @Transactional(readOnly = true)
    public TabelaPrecoResumoResponseDTO buscarExecucao(Long execucaoId) {
        TabelaPrecoExecucao execucao = execucaoRepository.findById(execucaoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Execução de importação de preço"));
        return TabelaPrecoResumoResponseDTO.from(execucao);
    }

    /** Preços (TABELA REGULAR) da unidade por grupo do plano, na execução vigente. */
    @Deprecated
    @Transactional(readOnly = true)
    public List<PrecoItem> listarPrecosPorProduto(Long produtoId) {
        EstoqueGeral unidade = estoqueGeralRepository.findById(produtoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Produto"));
        TabelaPrecoExecucao e = exigirVigente();
        String chave = precoVigenteService.chaveNomeDe(unidade, e.getId());
        return precoItemRepository.findByExecucaoIdAndChaveNomeOrderByTabelaAscGrupoPlanoAsc(e.getId(), chave).stream()
                .filter(p -> p.getTabela() == TabelaPreco.REGULAR)
                .toList();
    }

    /** Grupos de plano com preço na execução vigente. */
    @Deprecated
    @Transactional(readOnly = true)
    public List<String> listarOfertasDisponiveis() {
        return precoItemRepository.gruposDistintos(exigirVigente().getId()).stream()
                .sorted()
                .map(GrupoPlano::name)
                .toList();
    }

    /** Itens com preço (TABELA REGULAR) para o grupo informado ("VIVO_V", "Vivo V", "PRÉ"...). */
    @Deprecated
    @Transactional(readOnly = true)
    public List<PrecoItem> listarProdutosPorOferta(String oferta) {
        GrupoPlano grupo = GrupoPlano.deRotulo(oferta)
                .orElseThrow(() -> new NegocioException("Oferta/grupo de plano desconhecido: '" + oferta + "'."));
        return precoItemRepository.findByExecucaoIdAndTabelaAndGrupoPlanoOrderByNomeComercialAsc(
                exigirVigente().getId(), TabelaPreco.REGULAR, grupo);
    }

    private TabelaPrecoExecucao exigirVigente() {
        return precoVigenteService.vigente()
                .orElseThrow(() -> new NegocioException("Nenhuma tabela de preço foi importada ainda."));
    }
}
