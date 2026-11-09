package com.vivo4redes.syscor.venda.enums;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/** Condição de pagamento da venda. Literais = CHECK chk_vendas_condicao_pagamento (V26). */
public enum CondicaoPagamento {
    /** PIX e Vivo Pay. */
    PIX(1, null),
    /** Crédito de 1x a 12x, débito, dinheiro e boleto, sem juros. */
    A_VISTA(12, null),
    PARCELADO_13(13, "1.0774494"),
    PARCELADO_14(14, "1.0831273"),
    PARCELADO_15(15, "1.0888245"),
    PARCELADO_16(16, "1.0945411"),
    PARCELADO_17(17, "1.1002770"),
    PARCELADO_18(18, "1.1060324"),
    PARCELADO_19(19, "1.1118073"),
    PARCELADO_20(20, "1.1176012"),
    PARCELADO_21(21, "1.1234148");

    private static final Set<FormaPagamento> FORMAS_PIX = EnumSet.of(FormaPagamento.PIX, FormaPagamento.VIVO_PAY);
    private static final Set<FormaPagamento> FORMAS_ENTRADA = EnumSet.of(FormaPagamento.DINHEIRO, FormaPagamento.PIX,
            FormaPagamento.VIVO_PAY, FormaPagamento.CARTAO_DEBITO);

    private final int parcelas;
    private final BigDecimal fator;

    CondicaoPagamento(int parcelas, String fator) {
        this.parcelas = parcelas;
        this.fator = fator == null ? null : new BigDecimal(fator);
    }

    /** Maior número de parcelas da condição: 1 no PIX, 12 em A_VISTA e n em PARCELADO_n. */
    public int parcelas() {
        return parcelas;
    }

    /** Fator de juros de PARCELADO_n; null em PIX e A_VISTA, que não têm juros. */
    public BigDecimal fator() {
        return fator;
    }

    public boolean parcelado() {
        return fator != null;
    }

    /** Forma usada para buscar o preço: null em A_VISTA, que fica no preço cheio. */
    public FormaPagamento formaPagamento() {
        if (this == PIX) return FormaPagamento.PIX;
        return parcelado() ? FormaPagamento.CARTAO_CREDITO : null;
    }

    /** Parcelas usadas para buscar o preço: só PARCELADO_n tem. */
    public Integer numeroParcelas() {
        return parcelado() ? parcelas : null;
    }

    /**
     * Se uma linha digitada cabe na condição. Em PARCELADO_n são as formas de entrada: o crédito é a linha
     * FINANCIADO, criada pelo backend.
     */
    public boolean aceita(FormaPagamento forma, int parcelasDaLinha) {
        if (this == PIX) return FORMAS_PIX.contains(forma);
        if (parcelado()) return FORMAS_ENTRADA.contains(forma);
        if (forma == FormaPagamento.VOUCHER) return false;
        return forma != FormaPagamento.CARTAO_CREDITO || parcelasDaLinha <= parcelas;
    }

    public static CondicaoPagamento parcelado(int parcelas) {
        return Arrays.stream(values())
                .filter(c -> c.parcelado() && c.parcelas == parcelas)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Sem condição para " + parcelas + "x."));
    }
}
