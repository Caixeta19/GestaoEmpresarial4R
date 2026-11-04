package com.vivo4redes.syscor.venda.dto.request;

import jakarta.validation.constraints.NotNull;

/** PATCH /vendas/{id}/renova — liga/desliga a TABELA RENOVA nos aparelhos da venda. */
public record RenovaRequestDTO(

        @NotNull(message = "ativo é obrigatório")
        Boolean ativo
) {
}
