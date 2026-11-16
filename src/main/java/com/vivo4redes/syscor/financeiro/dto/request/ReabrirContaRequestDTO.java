package com.vivo4redes.syscor.financeiro.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReabrirContaRequestDTO(
        @NotBlank(message = "justificativa é obrigatória")
        @Size(min = 10, max = 300, message = "justificativa deve ter de 10 a 300 caracteres")
        String justificativa
) {
}
