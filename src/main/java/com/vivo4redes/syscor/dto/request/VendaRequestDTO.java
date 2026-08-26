package com.vivo4redes.syscor.dto.request;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * Abre um novo carrinho de venda (status ABERTA) para o cliente informado.
 * "itens" é opcional aqui: o fluxo padrão da UI é abrir o carrinho vazio e
 * ir adicionando itens pelas abas (POST /vendas/{id}/itens). A validação de
 * "pelo menos um item" só é exigida ao finalizar (US-302), não ao abrir.
 */
public record VendaRequestDTO(

        @NotNull(message = "clienteId é obrigatório")
        Long clienteId,

        @Valid
        List<ItemVendaRequestDTO> itens
) {
}