package com.vivo4redes.syscor.financeiro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Descarta o lançamento (tarifa, IOF, transferência...). */
public record IgnorarLancamentoRequestDTO(
        @NotBlank(message = "motivo é obrigatório")
        @Size(min = 10, max = 300, message = "motivo deve ter de 10 a 300 caracteres")
        String motivo
) {
}
