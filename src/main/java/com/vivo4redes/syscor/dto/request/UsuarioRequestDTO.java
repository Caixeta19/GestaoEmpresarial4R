package com.vivo4redes.syscor.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UsuarioRequestDTO(

        @NotBlank(message = "nome é obrigatório")
        String nome,

        @NotBlank(message = "O login é obrigatório")
        String login,

        @NotBlank(message = "email é obrigatório")
        @Email(message = "email inválido")
        String email,

        @NotBlank(message = "senha é obrigatória")
        @Size(min = 8, message = "senha deve ter ao menos 8 caracteres")
        String senha,

        @NotBlank(message = "filial é obrigatória")
        @Size(min = 8, message = "a filial deve ser informada")
        String filial,

        @NotBlank(message = "cargo é obrigatório")
        @Size(min = 8, message = "o cargo deve ser informado ")
        String cargo
) {
}
