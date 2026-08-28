package com.vivo4redes.syscor.dto.request;
import com.vivo4redes.syscor.dto.AutenticacaoUsuarioDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record FinalizarVendaRequestDTO(

        @Valid
        @NotNull(message = "autenticacaoVendedor é obrigatória")
        AutenticacaoUsuarioDTO autenticacaoVendedor
) {
}