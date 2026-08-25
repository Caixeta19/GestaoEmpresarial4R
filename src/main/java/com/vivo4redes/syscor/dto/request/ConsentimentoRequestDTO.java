package com.vivo4redes.syscor.dto.request;

import jakarta.validation.constraints.NotNull;

public record ConsentimentoRequestDTO(

        @NotNull(message = "Informe true (opt-in) ou false (opt-out)")
        Boolean consentimentoMarketing,

        /** Obrigatório quando consentimentoMarketing = true. */
        String versaoTermoConsentimento
) {
}
