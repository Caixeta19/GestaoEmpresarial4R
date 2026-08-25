package com.vivo4redes.syscor.dto.request;

import com.vivo4redes.syscor.enums.TipoPessoa;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClienteRequestDTO(

        @NotNull(message = "Tipo de pessoa é obrigatório")
        TipoPessoa tipoPessoa,

        @NotBlank(message = "Nome é obrigatório")
        String nome,

        @NotBlank(message = "CPF/CNPJ é obrigatório")
        String cpfCnpj,

        @Email(message = "E-mail inválido")
        String email,

        String telefone,

        @NotNull(message = "É necessário informar explicitamente o consentimento de marketing (opt-in), mesmo que false")
        Boolean consentimentoMarketing,

        /** Obrigatório somente quando consentimentoMarketing = true — validado no service. */
        String versaoTermoConsentimento
) {
}
