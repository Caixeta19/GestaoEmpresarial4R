package com.vivo4redes.syscor.financeiro.dto.caixa;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Estorno de um lançamento: justificativa de no mínimo 10 caracteres (validada no serviço, 422) e chave de idempotência. */
public record EstornoRequestDTO(
        @Size(max = 300, message = "justificativa aceita até 300 caracteres")
        String justificativa,

        @NotBlank(message = "chaveIdempotencia é obrigatória")
        @Size(max = 80, message = "chaveIdempotencia aceita até 80 caracteres")
        String chaveIdempotencia
) {
}
