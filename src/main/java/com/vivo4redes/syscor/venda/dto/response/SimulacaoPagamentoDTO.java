package com.vivo4redes.syscor.venda.dto.response;

import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;

import java.math.BigDecimal;

/**
 * Uma condição na simulação da venda, já com as entradas lançadas.
 *
 * @param parcelas        1 no PIX, 12 em A_VISTA e n em PARCELADO_n
 * @param valorParcela    financiado ÷ n em PARCELADO_n; total ÷ parcelas nas demais
 * @param economiaOuJuros total − preço cheio: negativo = desconto PIX, positivo = juros
 */
public record SimulacaoPagamentoDTO(CondicaoPagamento condicao, BigDecimal total, int parcelas,
                                    BigDecimal valorParcela, BigDecimal economiaOuJuros) {
}
