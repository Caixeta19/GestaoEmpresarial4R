package com.vivo4redes.syscor.dto.request;

import com.vivo4redes.syscor.enums.CategoriaItemVenda;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ItemVendaRequestDTO(

        @NotNull(message = "categoria é obrigatória (PRODUTO_VIVO, SERVICO_VIVO ou RECARGA)")
        CategoriaItemVenda categoria,

        @NotNull(message = "produtoId é obrigatório")
        Long produtoId,

        @NotBlank(message = "descricaoProduto é obrigatória")
        String descricaoProduto,

        @NotNull(message = "quantidade é obrigatória")
        @DecimalMin(value = "0.001", message = "quantidade deve ser maior que zero")
        BigDecimal quantidade,

        @NotNull(message = "valorUnitario é obrigatório")
        @DecimalMin(value = "0.01", message = "valorUnitario deve ser maior que zero")
        BigDecimal valorUnitario
) {
}