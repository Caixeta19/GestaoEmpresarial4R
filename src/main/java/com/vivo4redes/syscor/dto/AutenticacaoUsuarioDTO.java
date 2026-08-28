package com.vivo4redes.syscor.dto;
import jakarta.validation.constraints.NotBlank;

public record AutenticacaoUsuarioDTO(

        @NotBlank(message = "email é obrigatório")
        String email,

        @NotBlank(message = "senha é obrigatória")
        String senha
) {
}
