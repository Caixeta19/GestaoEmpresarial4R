package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.response.OpcaoPagamentoDTO;
import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Fórmula das condições de pagamento da tabela da Telefônica (conferida na vigência de 29/10/2026).
 * <ul>
 *   <li>PIX / Vivo Pay: 90% do preço cheio.</li>
 *   <li>À vista e 2x a 12x: preço cheio, sem juros.</li>
 *   <li>13x a 21x: preço cheio × fator (juros ≈ 1,0831% a.m., tabela Price).</li>
 *   <li>Renova: PIX e 13x–21x sobre o preço Regular, menos o desconto do fabricante.</li>
 *   <li>Entrada no parcelado: os juros incidem só sobre o que sobra da base de tabela.</li>
 *   <li>Itens sem tabela (chip, serviço, recarga): valor cheio em qualquer condição.</li>
 * </ul>
 * A planilha prevalece: a fórmula só preenche o que falta e serve para conferir a importação.
 */
public final class CalculadoraCondicaoPagamento {

    public static final int PARCELAS_SEM_JUROS = 12;
    public static final int PRIMEIRA_PARCELA_COM_JUROS = 13;
    public static final int ULTIMA_PARCELA = 21;

    private static final int CASAS = 2;
    private static final BigDecimal FATOR_PIX = new BigDecimal("0.9");
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.01");

    private CalculadoraCondicaoPagamento() {
    }

    /** @param desconto desconto do fabricante (null ou zero na REGULAR) */
    public static BigDecimal pix(BigDecimal regular, BigDecimal desconto) {
        return arredondar(regular.multiply(FATOR_PIX)).subtract(zeroSeNulo(desconto));
    }

    /** @param desconto desconto do fabricante (null ou zero na REGULAR) */
    public static BigDecimal totalParcelado(BigDecimal regular, int parcelas, BigDecimal desconto) {
        return arredondar(regular.multiply(CondicaoPagamento.parcelado(parcelas).fator())).subtract(zeroSeNulo(desconto));
    }

    public static BigDecimal valorParcela(BigDecimal total, int parcelas) {
        return total.divide(BigDecimal.valueOf(parcelas), CASAS, RoundingMode.HALF_UP);
    }

    /**
     * Total da venda numa condição. Sem entrada, vale a soma dos itens de tabela nessa condição (a planilha, centavo a
     * centavo). Com entrada em PARCELADO_n: {@code E + round((baseTabela − E) × fator) − desconto + semTabela}, com
     * {@code E} limitada à base de tabela; o que passar dela abate os itens sem tabela, que não têm juros.
     *
     * @param entrada soma das linhas de entrada (só conta em PARCELADO_n)
     */
    public static TotalVenda totalDaVenda(CondicaoPagamento condicao, ComposicaoVenda venda, BigDecimal entrada) {
        boolean comEntrada = condicao.parcelado() && entrada.signum() > 0;
        BigDecimal tabela = comEntrada ? tabelaComEntrada(condicao, venda, entrada) : venda.tabelaNaCondicao();
        BigDecimal total = tabela.add(venda.semTabela());
        BigDecimal financiado = condicao.parcelado() ? total.subtract(entrada).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        BigDecimal parcelado = condicao.parcelado() ? financiado : total;
        return new TotalVenda(total, total.subtract(venda.valorItens()), financiado,
                valorParcela(parcelado, condicao.parcelas()));
    }

    private static BigDecimal tabelaComEntrada(CondicaoPagamento condicao, ComposicaoVenda venda, BigDecimal entrada) {
        BigDecimal base = venda.cheioTabela().add(venda.descontoFabricante());
        BigDecimal abatido = entrada.min(base);
        return abatido.add(arredondar(base.subtract(abatido).multiply(condicao.fator())))
                .subtract(venda.descontoFabricante());
    }

    /** Diferença acima de R$ 0,01 entre o valor da planilha e o da fórmula. */
    public static boolean diverge(BigDecimal planilha, BigDecimal calculado) {
        return planilha.subtract(calculado).abs().compareTo(TOLERANCIA) > 0;
    }

    /**
     * Valores de todas as condições de um preço: o que veio da planilha e, no que faltar, a fórmula.
     *
     * @param preco    preço cheio da linha (Regular ou Renova)
     * @param precoPix PIX da planilha (null se faltou)
     * @param parcelas totais de 13x a 21x da planilha (null ou incompleto se faltaram)
     * @param regular  preço Regular, base da fórmula (na linha Regular, o próprio {@code preco})
     * @param desconto desconto do fabricante (null ou zero na REGULAR)
     */
    public static ValoresCondicao completar(BigDecimal preco, BigDecimal precoPix, Map<Integer, BigDecimal> parcelas,
                                            BigDecimal regular, BigDecimal desconto) {
        BigDecimal pix = precoPix != null ? precoPix : pix(regular, desconto);
        Map<Integer, BigDecimal> totais = new TreeMap<>();
        for (int n = PRIMEIRA_PARCELA_COM_JUROS; n <= ULTIMA_PARCELA; n++) {
            BigDecimal daPlanilha = parcelas == null ? null : parcelas.get(n);
            totais.put(n, daPlanilha != null ? daPlanilha : totalParcelado(regular, n, desconto));
        }
        return new ValoresCondicao(preco, pix, totais);
    }

    private static BigDecimal arredondar(BigDecimal valor) {
        return valor.setScale(CASAS, RoundingMode.HALF_UP);
    }

    private static BigDecimal zeroSeNulo(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    /**
     * O que compõe o total da venda.
     *
     * @param cheioTabela        itens de tabela no preço cheio (na Renova, o preço cheio Renova)
     * @param tabelaNaCondicao   itens de tabela no preço da condição, sem entrada
     * @param descontoFabricante desconto do fabricante dos itens na Renova; cheioTabela + desconto = Regular
     * @param semTabela          itens sem tabela e chips, que não mudam com a condição
     */
    public record ComposicaoVenda(BigDecimal cheioTabela, BigDecimal tabelaNaCondicao, BigDecimal descontoFabricante,
                                  BigDecimal semTabela) {

        /** Soma no preço cheio, sem juros nem desconto PIX. */
        public BigDecimal valorItens() {
            return cheioTabela.add(semTabela);
        }
    }

    /**
     * @param ajuste       total − preço cheio: negativo = desconto PIX, positivo = juros
     * @param financiado   saldo no crédito (total − entrada); zero fora de PARCELADO_n
     * @param valorParcela financiado ÷ n em PARCELADO_n; total ÷ parcelas da condição nas demais
     */
    public record TotalVenda(BigDecimal total, BigDecimal ajuste, BigDecimal financiado, BigDecimal valorParcela) {
    }

    /** Total de cada condição: {@code cheio} (até 12x), {@code pix} e {@code totais} (13x a 21x, sempre completo). */
    public record ValoresCondicao(BigDecimal cheio, BigDecimal pix, Map<Integer, BigDecimal> totais) {

        /** PIX, A_VISTA de 1x a 12x e PARCELADO_13 a PARCELADO_21, nessa ordem. */
        public List<OpcaoPagamentoDTO> opcoes() {
            List<OpcaoPagamentoDTO> opcoes = new ArrayList<>();
            opcoes.add(new OpcaoPagamentoDTO(CondicaoPagamento.PIX, 1, pix, pix));
            for (int n = 1; n <= PARCELAS_SEM_JUROS; n++) {
                opcoes.add(new OpcaoPagamentoDTO(CondicaoPagamento.A_VISTA, n, cheio, valorParcela(cheio, n)));
            }
            totais.forEach((n, total) ->
                    opcoes.add(new OpcaoPagamentoDTO(CondicaoPagamento.parcelado(n), n, total, valorParcela(total, n))));
            return opcoes;
        }
    }
}
