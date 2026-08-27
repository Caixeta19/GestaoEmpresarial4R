package com.vivo4redes.syscor.dto.request;

import jakarta.validation.constraints.NotBlank;

public record FilialRequestDTO(

        @NotBlank(message = "codigo é obrigatório")
        String codigo,

        @NotBlank(message = "nome é obrigatório")
        String nome
) {
}
