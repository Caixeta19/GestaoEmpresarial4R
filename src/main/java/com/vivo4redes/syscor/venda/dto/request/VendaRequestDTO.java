package com.vivo4redes.syscor.venda.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * Abre um novo carrinho de venda (status ABERTA). Reflete a tela "Início":
 * cliente, filial e estoqueAvancado vêm dela. Não pede senha: o vendedor
 * provisório é o usuário logado, e uma autenticação enviada pelo front antigo
 * é ignorada. Os itens são adicionados depois, incrementalmente, pelas abas
 * (POST /vendas/{id}/itens).
 */
public record VendaRequestDTO(

        @NotNull(message = "clienteId é obrigatório")
        Long clienteId,

        @NotNull(message = "filialId é obrigatório")
        Long filialId,

        @NotNull(message = "estoqueAvancado é obrigatório (true/false)")
        Boolean estoqueAvancado
) {
}
