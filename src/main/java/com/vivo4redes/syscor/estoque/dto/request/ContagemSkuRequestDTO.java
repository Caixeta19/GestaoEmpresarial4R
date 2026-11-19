package com.vivo4redes.syscor.estoque.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Contagem manual de um SKU (acessório com quantidade: a bipagem do serial conta 1). {@code quantidade} nula remove a
 * contagem manual; com quantidade, a justificativa é obrigatória.
 */
public record ContagemSkuRequestDTO(
        @DecimalMin(value = "0", message = "A quantidade contada não pode ser negativa.")
        BigDecimal quantidade,
        @Size(max = 300, message = "Justificativa com no máximo 300 caracteres.")
        String justificativa
) {
}
