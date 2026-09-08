package com.vivo4redes.syscor.venda.dto.request;

import com.vivo4redes.syscor.venda.dto.AutenticacaoUsuarioDTO;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

public record StatusVendaRequestDTO(

        @NotNull(message = "novoStatus é obrigatório")
        StatusVenda novoStatus,

        @Valid
        @NotNull(message = "autenticacaoVendedor é obrigatória")
        AutenticacaoUsuarioDTO autenticacaoUsuario
) {

}