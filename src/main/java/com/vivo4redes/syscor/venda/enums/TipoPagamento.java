package com.vivo4redes.syscor.venda.enums;

/** Papel da linha de pagamento na venda. Literais = CHECK chk_pagamentos_venda_tipo (V27). */
public enum TipoPagamento {
    /** Linha de venda em PIX ou A_VISTA. */
    PAGAMENTO,
    /** Linha paga antes do financiamento em PARCELADO_N: abate a base dos juros. */
    ENTRADA,
    /** Saldo no crédito em PARCELADO_N, criado e recalculado pelo backend. */
    FINANCIADO
}
