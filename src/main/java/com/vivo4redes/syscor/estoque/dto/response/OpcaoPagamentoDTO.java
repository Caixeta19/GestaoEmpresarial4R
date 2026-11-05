package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;

import java.math.BigDecimal;

/** Uma opção de pagamento do item: {@code total} em {@code parcelas} vezes de {@code valorParcela}. */
public record OpcaoPagamentoDTO(CondicaoPagamento condicao, int parcelas, BigDecimal total, BigDecimal valorParcela) {
}
