package com.vivo4redes.syscor.dto.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record FinalizarVendaRequestDTO(

        @Valid
        @NotNull(message = "autenticacaoVendedor é obrigatória")
        AutenticacaoVendedorDTO autenticacaoVendedor
) {
}
