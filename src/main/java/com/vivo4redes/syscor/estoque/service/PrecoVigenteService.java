package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.model.PlanoVivo;
import com.vivo4redes.syscor.estoque.model.PrecoItem;
import com.vivo4redes.syscor.estoque.model.ProdutoVivo;
import com.vivo4redes.syscor.estoque.model.TabelaPrecoExecucao;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.estoque.repository.PlanoVivoRepository;
import com.vivo4redes.syscor.estoque.repository.PrecoItemRepository;
import com.vivo4redes.syscor.estoque.repository.ProdutoVivoRepository;
import com.vivo4redes.syscor.estoque.repository.TabelaPrecoExecucaoRepository;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.util.EstoqueUtil;
import com.vivo4redes.syscor.util.TextoUtil;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Preço da tabela da Telefônica VIGENTE (a execução mais recente).
 * <ul>
 *   <li>Regra 1 — estoque -> Nome Comercial: {@code codigo_acessorio} presente em PRODUTOS.Cod_Sap usa aquele
 *       Nome Comercial; senão, o {@code modelo_comercial} normalizado.</li>
 *   <li>Regras 2–5 e 9 — preço do item por plano, tabela e forma de pagamento ({@link #calcular}).</li>
 *   <li>Regra 6 — chip com preço fixo de configuração ({@link #precoChip()}).</li>
 *   <li>Regra 7 — referência no estoque: {@code valor_venda} = preço cheio REGULAR do grupo PRE (ou SEM_PLANO,
 *       para itens que só existem nas abas sem plano), só em unidades DISPONIVEL e RESERVADO.</li>
 * </ul>
 */
@Service
public class PrecoVigenteService {

    /** Unidades que recebem o preço de referência; VENDIDO guarda o preço da época e DEFEITO não é vendido. */
    static final Set<StatusEstoque> STATUS_COM_PRECO_VIGENTE = EnumSet.of(StatusEstoque.DISPONIVEL, StatusEstoque.RESERVADO);

    private static final int PARCELAS_NO_PRECO_CHEIO = 12;
    private static final int MAXIMO_PARCELAS = 21;

    private final TabelaPrecoExecucaoRepository execucaoRepository;
    private final PrecoItemRepository precoItemRepository;
    private final ProdutoVivoRepository produtoVivoRepository;
    private final PlanoVivoRepository planoVivoRepository;
    private final EstoqueGeralRepository estoqueGeralRepository;
    private final BigDecimal precoChip;

    public PrecoVigenteService(TabelaPrecoExecucaoRepository execucaoRepository,
                               PrecoItemRepository precoItemRepository,
                               ProdutoVivoRepository produtoVivoRepository,
                               PlanoVivoRepository planoVivoRepository,
                               EstoqueGeralRepository estoqueGeralRepository,
                               @Value("${syscor.precos.chip:13.00}") BigDecimal precoChip) {
        this.execucaoRepository = execucaoRepository;
        this.precoItemRepository = precoItemRepository;
        this.produtoVivoRepository = produtoVivoRepository;
        this.planoVivoRepository = planoVivoRepository;
        this.estoqueGeralRepository = estoqueGeralRepository;
        this.precoChip = precoChip.setScale(2, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public Optional<TabelaPrecoExecucao> vigente() {
        return execucaoRepository.findTopByOrderByIdDesc();
    }

    /** Regra 6: preço do chip vem da configuração ({@code syscor.precos.chip}), não da tabela. */
    public BigDecimal precoChip() {
        return precoChip;
    }

    // ------------------------------------------------------------------ preço do item (regras 1–5 e 9)

    /**
     * Preço do item na tabela vigente.
     * <ol>
     *   <li>Nome Comercial pelo Cod_Sap (código de acessório) ou pelo modelo normalizado.</li>
     *   <li>Grupo pelo plano do cliente (aba PLANOS); sem plano -> PRE.</li>
     *   <li>Tabela informada (ver {@link ResolvedorTabela}). RENOVA não existe para PRE (422); produto sem linha
     *       Renova fica no preço REGULAR do mesmo grupo.</li>
     *   <li>Item que só existe nas abas sem plano: SEM_PLANO / REGULAR, qualquer que seja o plano.</li>
     *   <li>Pagamento: PIX -> preço PIX; crédito em 13x–21x -> total da parcela; demais -> preço cheio.</li>
     * </ol>
     *
     * @throws NegocioException (422) sem tabela importada, plano desconhecido, parcelamento inválido ou item sem preço
     */
    @Transactional(readOnly = true)
    public PrecoCalculado calcular(EstoqueGeral item, String plano, TabelaPreco tabela,
                                   FormaPagamento formaPagamento, Integer numeroParcelas) {
        TabelaPrecoExecucao execucao = vigente()
                .orElseThrow(() -> new NegocioException("Nenhuma tabela de preço foi importada ainda."));
        Long execucaoId = execucao.getId();
        String chave = chaveNomeDe(item, execucaoId);
        String nomeExibido = item.getModeloComercial() != null ? item.getModeloComercial() : chave;
        GrupoPlano grupo = grupoDoPlano(plano, execucaoId);
        TabelaPreco tabelaPedida = tabela == null ? TabelaPreco.REGULAR : tabela;
        if (tabelaPedida == TabelaPreco.RENOVA && grupo == GrupoPlano.PRE) {
            // O bloco Renova da planilha não tem o grupo PRÉ.
            throw new NegocioException("Renova não disponível para plano pré-pago.");
        }

        Optional<PrecoItem> pedido = precoItemRepository.buscar(execucaoId, chave, tabelaPedida, List.of(grupo)).stream().findFirst();
        if (pedido.isEmpty() && tabelaPedida == TabelaPreco.RENOVA) {
            pedido = precoItemRepository.buscar(execucaoId, chave, TabelaPreco.REGULAR, List.of(grupo)).stream().findFirst();
        }
        PrecoItem preco = pedido
                .or(() -> precoItemRepository.buscar(execucaoId, chave, TabelaPreco.REGULAR, List.of(GrupoPlano.SEM_PLANO))
                        .stream().findFirst())
                .orElseThrow(() -> new NegocioException("Item sem preço na tabela vigente (" + nomeExibido + ")."));

        BigDecimal valor = valorParaPagamento(preco, formaPagamento, numeroParcelas).setScale(2, RoundingMode.HALF_UP);
        BigDecimal desconto = preco.getTabela() != TabelaPreco.RENOVA ? null
                : (preco.getDescontoFabricante() == null ? BigDecimal.ZERO : preco.getDescontoFabricante())
                        .setScale(2, RoundingMode.HALF_UP);
        return new PrecoCalculado(execucaoId, execucao.getVigenciaEm(), preco.getNomeComercial(), preco.getTabela(),
                preco.getGrupoPlano(), preco.getPreco(), preco.getPrecoPix(), preco.getParcelas(),
                formaPagamento, numeroParcelas, valor, desconto);
    }

    /** Regra 1, consultando os produtos gravados da execução. */
    String chaveNomeDe(EstoqueGeral item, Long execucaoId) {
        if (EstoqueUtil.ehAcessorio(item)) {
            String codigo = item.getCodigoAcessorio().trim().toUpperCase(Locale.ROOT);
            List<ProdutoVivo> produtos = produtoVivoRepository.buscarPorCodSap(execucaoId, codigo);
            if (!produtos.isEmpty()) return produtos.get(0).getChaveNome();
        }
        return TextoUtil.chave(item.getModeloComercial());
    }

    /**
     * Regra 2: grupo do plano pelo GRUPAMENTO ATUAL da aba PLANOS (nome normalizado, COD SISTEMA ou COD SAP).
     * Sem plano ou pré-pago -> PRE. Aceita também o próprio nome do grupo ("VIVO V", "CONTROLE_ALTO_VALOR").
     */
    GrupoPlano grupoDoPlano(String plano, Long execucaoId) {
        if (EstoqueUtil.textoVazio(plano)) return GrupoPlano.PRE;
        String chave = TextoUtil.chave(plano);
        List<PlanoVivo> planos = planoVivoRepository.buscarPorNomeOuCodigo(execucaoId, chave);
        if (!planos.isEmpty()) return planos.get(0).getGrupoPlano();
        Optional<GrupoPlano> peloRotulo = GrupoPlano.deRotulo(plano).filter(g -> g != GrupoPlano.SEM_PLANO);
        if (peloRotulo.isPresent()) return peloRotulo.get();
        if (chave.replace("-", " ").contains("PRE PAGO") || chave.contains("PREPAGO")) return GrupoPlano.PRE;
        throw new NegocioException("Plano '" + plano.trim() + "' não encontrado na tabela de preços vigente.");
    }

    private BigDecimal valorParaPagamento(PrecoItem preco, FormaPagamento forma, Integer numeroParcelas) {
        if (forma == FormaPagamento.PIX) {
            return preco.getPrecoPix() != null ? preco.getPrecoPix() : preco.getPreco();
        }
        if (forma == FormaPagamento.CARTAO_CREDITO && numeroParcelas != null) {
            if (numeroParcelas < 1 || numeroParcelas > MAXIMO_PARCELAS) {
                throw new NegocioException("Parcelamento inválido: " + numeroParcelas + "x (de 1x a " + MAXIMO_PARCELAS + "x).");
            }
            if (numeroParcelas > PARCELAS_NO_PRECO_CHEIO) {
                BigDecimal valor = preco.getParcelas() == null ? null : preco.getParcelas().get(numeroParcelas);
                if (valor == null) {
                    throw new NegocioException("Item sem preço em " + numeroParcelas + "x na tabela vigente ("
                            + preco.getNomeComercial() + ").");
                }
                return valor;
            }
        }
        return preco.getPreco();
    }

    // ------------------------------------------------------------------ referência no estoque (regra 7)

    /**
     * Índices de uma execução para resolver estoque -> preço de referência sem N+1:
     * Cod_Sap (maiúsculo) -> chave do Nome Comercial, e chave -> preço de referência.
     */
    record IndicesReferencia(Map<String, String> chavePorCodSap, Map<String, BigDecimal> referenciaPorChave) {

        String chaveDe(EstoqueGeral e) {
            if (EstoqueUtil.ehAcessorio(e)) {
                String chave = chavePorCodSap.get(e.getCodigoAcessorio().trim().toUpperCase(Locale.ROOT));
                if (chave != null) return chave;
            }
            return TextoUtil.chave(e.getModeloComercial());
        }

        BigDecimal referenciaDe(EstoqueGeral e) {
            return referenciaPorChave.get(chaveDe(e));
        }
    }

    /** Índices montados a partir do que está gravado para a execução (usado fora do upload). */
    IndicesReferencia indicesDaExecucao(Long execucaoId) {
        Map<String, String> chavePorCodSap = new HashMap<>();
        for (Object[] r : produtoVivoRepository.indiceCodSap(execucaoId)) {
            chavePorCodSap.putIfAbsent((String) r[0], (String) r[1]);
        }
        Map<String, BigDecimal> referencia = new HashMap<>();
        // PRE primeiro; SEM_PLANO só para itens que não têm preço por plano (abas sem plano, regra 4).
        // Assim um aparelho que também aparece numa aba sem plano (ex.: Demo) não herda o preço de demonstração.
        for (GrupoPlano grupo : List.of(GrupoPlano.PRE, GrupoPlano.SEM_PLANO)) {
            for (Object[] r : precoItemRepository.precosDeReferencia(execucaoId, grupo)) {
                referencia.putIfAbsent((String) r[0], (BigDecimal) r[1]);
            }
        }
        return new IndicesReferencia(chavePorCodSap, referencia);
    }

    /** Resultado de aplicar a referência: unidades que receberam preço e modelos que ficaram sem. */
    record ResultadoReferencia(int unidadesAtualizadas, SortedSet<String> modelosSemPreco) {
    }

    /**
     * Grava em {@code valor_venda} o preço de referência das unidades DISPONIVEL/RESERVADO informadas.
     * Unidade sem preço na tabela mantém o valor atual (e entra em {@code modelosSemPreco}).
     */
    ResultadoReferencia aplicarReferencia(Collection<EstoqueGeral> unidades, IndicesReferencia indices) {
        int atualizadas = 0;
        SortedSet<String> semPreco = new TreeSet<>();
        List<EstoqueGeral> alteradas = new ArrayList<>();
        for (EstoqueGeral e : unidades) {
            if (!STATUS_COM_PRECO_VIGENTE.contains(e.getStatus())) continue;
            BigDecimal referencia = indices.referenciaDe(e);
            if (referencia == null) {
                semPreco.add(e.getModeloComercial() == null ? e.getSerial() : e.getModeloComercial());
                continue;
            }
            atualizadas++;
            if (e.getValorVenda() == null || e.getValorVenda().compareTo(referencia) != 0) {
                e.setValorVenda(referencia);
                alteradas.add(e);
            }
        }
        estoqueGeralRepository.saveAll(alteradas);
        return new ResultadoReferencia(atualizadas, semPreco);
    }

    /**
     * Estoque novo/atualizado pela importação do relatório Vivo recebe o preço da tabela vigente.
     * Sem tabela importada ainda, não faz nada.
     */
    @Transactional
    public ResultadoReferencia aplicarPrecoVigente(Collection<EstoqueGeral> unidades) {
        return vigente()
                .map(execucao -> aplicarReferencia(unidades, indicesDaExecucao(execucao.getId())))
                .orElseGet(() -> new ResultadoReferencia(0, new TreeSet<>()));
    }
}
