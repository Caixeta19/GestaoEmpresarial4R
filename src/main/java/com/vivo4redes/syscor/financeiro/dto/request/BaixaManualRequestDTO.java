package com.vivo4redes.syscor.financeiro.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Baixa de conta paga por outro meio (fora do extrato). */
public record BaixaManualRequestDTO(
        @NotNull(message = "dataPagamento é obrigatória")
        LocalDate dataPagamento,

        @NotNull(message = "valorPago é obrigatório")
        @DecimalMin(value = "0.01", message = "valorPago deve ser maior que zero")
        @Digits(integer = 12, fraction = 2, message = "valorPago deve ter no máximo 2 casas decimais")
        BigDecimal valorPago,

        @NotBlank(message = "banco é obrigatório")
        @Size(max = 30, message = "banco aceita até 30 caracteres")
        String banco,

        @NotBlank(message = "justificativa é obrigatória")
        @Size(min = 10, max = 300, message = "justificativa deve ter de 10 a 300 caracteres")
        String justificativa
) {
}
