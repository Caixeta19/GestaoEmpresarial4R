package com.vivo4redes.syscor.dto.request;

import com.vivo4redes.syscor.dto.AutenticacaoVendedorDTO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Abre um novo carrinho de venda (status ABERTA). Reflete a tela "Início":
 * cliente, filial e estoqueAvancado vêm dela, e a autenticação do vendedor
 * é exigida para confirmar quem está registrando a venda. Os itens são
 * adicionados depois, incrementalmente, pelas abas (POST /vendas/{id}/itens).
 */
public record VendaRequestDTO(

        @NotNull(message = "clienteId é obrigatório")
        Long clienteId,

        @NotNull(message = "filialId é obrigatório")
        Long filialId,

        @NotNull(message = "estoqueAvancado é obrigatório (true/false)")
        Boolean estoqueAvancado,

        @Valid
        @NotNull(message = "autenticacaoVendedor é obrigatória")
        AutenticacaoVendedorDTO autenticacaoVendedor
) {
}