package com.vivo4redes.syscor.venda.dto.request;

import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;
import jakarta.validation.constraints.NotNull;

/** PATCH /vendas/{id}/condicao-pagamento — troca a condição e reprecifica a venda. */
public record CondicaoPagamentoRequestDTO(

        @NotNull(message = "condicaoPagamento é obrigatória")
        CondicaoPagamento condicaoPagamento
) {
}
