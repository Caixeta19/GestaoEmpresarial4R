package com.vivo4redes.syscor.financeiro.dto.caixa;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Abertura sugerida do dinheiro: o valor contabilizado do último caixa fechado desse caixa. null = nunca fechou. */
public record SugestaoAberturaResponseDTO(BigDecimal dinheiro, LocalDate dataUltimoFechamento) {
}
