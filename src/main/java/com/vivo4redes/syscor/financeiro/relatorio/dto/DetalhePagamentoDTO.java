package com.vivo4redes.syscor.financeiro.relatorio.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Uma linha de pagamento para o detalhe da exportação ({@code dataHora} no fuso local). */
public record DetalhePagamentoDTO(LocalDateTime dataHora, Long numeroVenda, String filial, String caixa,
                                  String forma, String formaRotulo, String tipo, BigDecimal valor, Integer parcelas) {
}
