package com.vivo4redes.syscor.venda.enums;

import java.util.Arrays;

/** Condição de pagamento da venda. Literais = CHECK chk_vendas_condicao_pagamento (V26). */
public enum CondicaoPagamento {
    /** PIX e Vivo Pay. */
    PIX(1),
    /** Crédito à vista, débito, dinheiro e de 2x a 12x sem juros. */
    A_VISTA(12),
    PARCELADO_13(13),
    PARCELADO_14(14),
    PARCELADO_15(15),
    PARCELADO_16(16),
    PARCELADO_17(17),
    PARCELADO_18(18),
    PARCELADO_19(19),
    PARCELADO_20(20),
    PARCELADO_21(21);

    private final int parcelasMaximas;

    CondicaoPagamento(int parcelasMaximas) {
        this.parcelasMaximas = parcelasMaximas;
    }

    /** Maior número de parcelas da condição: 1 no PIX, 12 em A_VISTA e n em PARCELADO_n. */
    public int parcelasMaximas() {
        return parcelasMaximas;
    }

    public boolean parcelado() {
        return this != PIX && this != A_VISTA;
    }

    /** Forma usada para buscar o preço: null em A_VISTA, que fica no preço cheio. */
    public FormaPagamento formaPagamento() {
        if (this == PIX) return FormaPagamento.PIX;
        return parcelado() ? FormaPagamento.CARTAO_CREDITO : null;
    }

    /** Parcelas usadas para buscar o preço: só PARCELADO_n tem. */
    public Integer numeroParcelas() {
        return parcelado() ? parcelasMaximas : null;
    }

    public static CondicaoPagamento parcelado(int parcelas) {
        return Arrays.stream(values())
                .filter(c -> c.parcelado() && c.parcelasMaximas == parcelas)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Sem condição para " + parcelas + "x."));
    }
}
