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
    private static final Map<Integer, BigDecimal> FATORES = Map.of(
            13, new BigDecimal("1.0774494"),
            14, new BigDecimal("1.0831273"),
            15, new BigDecimal("1.0888245"),
            16, new BigDecimal("1.0945411"),
            17, new BigDecimal("1.1002770"),
            18, new BigDecimal("1.1060324"),
            19, new BigDecimal("1.1118073"),
            20, new BigDecimal("1.1176012"),
            21, new BigDecimal("1.1234148"));

    private CalculadoraCondicaoPagamento() {
    }

    /** @param desconto desconto do fabricante (null ou zero na REGULAR) */
    public static BigDecimal pix(BigDecimal regular, BigDecimal desconto) {
        return arredondar(regular.multiply(FATOR_PIX)).subtract(zeroSeNulo(desconto));
    }

    /** @param desconto desconto do fabricante (null ou zero na REGULAR) */
    public static BigDecimal totalParcelado(BigDecimal regular, int parcelas, BigDecimal desconto) {
        BigDecimal fator = FATORES.get(parcelas);
        if (fator == null) {
            throw new IllegalArgumentException("Sem fator para " + parcelas + "x.");
        }
        return arredondar(regular.multiply(fator)).subtract(zeroSeNulo(desconto));
    }

    public static BigDecimal valorParcela(BigDecimal total, int parcelas) {
        return total.divide(BigDecimal.valueOf(parcelas), CASAS, RoundingMode.HALF_UP);
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
