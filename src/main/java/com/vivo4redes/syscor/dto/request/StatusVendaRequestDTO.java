package com.vivo4redes.syscor.dto.request;

import com.vivo4redes.syscor.enums.StatusVenda;
import jakarta.validation.constraints.NotNull;

public record StatusVendaRequestDTO(

    @NotNull(message = "novostatus é obrigatório")
    StatusVenda novoStatus
) {

}
