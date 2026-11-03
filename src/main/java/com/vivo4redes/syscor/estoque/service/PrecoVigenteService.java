package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.model.TabelaPrecoExecucao;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.estoque.repository.PrecoItemRepository;
import com.vivo4redes.syscor.estoque.repository.ProdutoVivoRepository;
import com.vivo4redes.syscor.estoque.repository.TabelaPrecoExecucaoRepository;
import com.vivo4redes.syscor.util.EstoqueUtil;
import com.vivo4redes.syscor.util.TextoUtil;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;

/**
 * Preço da tabela da Telefônica VIGENTE (a execução mais recente).
 * <ul>
 *   <li>Regra 1 — estoque -> Nome Comercial: {@code codigo_acessorio} presente em PRODUTOS.Cod_Sap usa aquele
 *       Nome Comercial; senão, o {@code modelo_comercial} normalizado.</li>
 *   <li>Regra 7 — referência no estoque: {@code valor_venda} = preço cheio REGULAR do grupo PRE (ou SEM_PLANO,
 *       para itens que só existem nas abas sem plano), só em unidades DISPONIVEL e RESERVADO.</li>
 * </ul>
 */
@Service
public class PrecoVigenteService {

    /** Unidades que recebem o preço de referência; VENDIDO guarda o preço da época e DEFEITO não é vendido. */
    static final Set<StatusEstoque> STATUS_COM_PRECO_VIGENTE = EnumSet.of(StatusEstoque.DISPONIVEL, StatusEstoque.RESERVADO);

    private final TabelaPrecoExecucaoRepository execucaoRepository;
    private final PrecoItemRepository precoItemRepository;
    private final ProdutoVivoRepository produtoVivoRepository;
    private final EstoqueGeralRepository estoqueGeralRepository;

    public PrecoVigenteService(TabelaPrecoExecucaoRepository execucaoRepository,
                               PrecoItemRepository precoItemRepository,
                               ProdutoVivoRepository produtoVivoRepository,
                               EstoqueGeralRepository estoqueGeralRepository) {
        this.execucaoRepository = execucaoRepository;
        this.precoItemRepository = precoItemRepository;
        this.produtoVivoRepository = produtoVivoRepository;
        this.estoqueGeralRepository = estoqueGeralRepository;
    }

    @Transactional(readOnly = true)
    public Optional<TabelaPrecoExecucao> vigente() {
        return execucaoRepository.findTopByOrderByIdDesc();
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
