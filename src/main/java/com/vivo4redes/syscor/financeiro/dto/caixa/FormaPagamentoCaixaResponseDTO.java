package com.vivo4redes.syscor.financeiro.dto.caixa;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;

import java.math.BigDecimal;

public record FormaPagamentoCaixaResponseDTO(
        Long id,
        FormaPagamento forma,
        BigDecimal abertura,
        BigDecimal movimento,
        /** Soma dos pagamentos das vendas CONCLUIDAS lançadas neste caixa (cancelada não entra). */
        BigDecimal entradas,
        /** Saídas lançadas; nas formas que liquidam na conciliação soma também as entradas (saídas = entradas). */
        BigDecimal saidas,
        /** abertura + movimento + entradas − saídas: o que deve ficar no caixa (dinheiro; nas outras formas, 0). */
        BigDecimal contabilizadoEsperado,
        /** Só preenchido depois do fechamento. */
        BigDecimal valorConferido,
        BigDecimal diferenca,
        /** true = PIX, Vivo Pay, cartões e boleto: liquidam na conciliação e não ficam na gaveta. */
        boolean liquidaNaConciliacao
) {
}
