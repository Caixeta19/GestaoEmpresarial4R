package com.vivo4redes.syscor.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record VendaRequestDTO(

        @NotNull(message = "clienteId é obrigatório")
        Long clienteId,

        @NotEmpty(message = "A venda precisa de pelo menos um item")
        @Valid
        List<ItemVendaRequestDTO> itens
) {
}
