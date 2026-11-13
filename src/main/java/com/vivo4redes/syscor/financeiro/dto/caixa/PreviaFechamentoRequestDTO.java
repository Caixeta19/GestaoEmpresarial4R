package com.vivo4redes.syscor.financeiro.dto.caixa;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Dinheiro que o gerente contou na gaveta, para a prévia da diferença antes de fechar. */
public record PreviaFechamentoRequestDTO(@NotNull(message = "valorContado é obrigatório") BigDecimal valorContado) {
}
