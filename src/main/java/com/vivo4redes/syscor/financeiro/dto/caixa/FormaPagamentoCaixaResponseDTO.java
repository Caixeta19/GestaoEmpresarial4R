package com.vivo4redes.syscor.financeiro.dto.caixa;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;

import java.math.BigDecimal;

public record FormaPagamentoCaixaResponseDTO(
        Long id,
        FormaPagamento forma,
        BigDecimal abertura,
        BigDecimal movimento,
        /** Calculado ao vivo somando os PagamentoVenda reais dessa filial/forma/dia. */
        BigDecimal entradas,
        BigDecimal saidas,
        /** = abertura + movimento + entradas - saidas. */
        BigDecimal contabilizadoEsperado,
        /** Só preenchido depois do fechamento. */
        BigDecimal valorConferido,
        BigDecimal diferenca
) {
}