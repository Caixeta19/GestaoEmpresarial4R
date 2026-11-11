package com.vivo4redes.syscor.estoque.dto.request;

import jakarta.validation.constraints.Size;

/** Justificativa de um pendente (ex.: "Não localizado fisicamente"). Texto vazio remove a justificativa. */
public record JustificativaRequestDTO(
        @Size(max = 300, message = "Justificativa com no máximo 300 caracteres.")
        String texto
) {
}
