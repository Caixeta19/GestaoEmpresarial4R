package com.vivo4redes.syscor.financeiro.dto.request;

import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record TituloFinanceiroRequestDTO(
        @NotNull(message = "tipo é obrigatório (RECEBER ou PAGAR)")
        TipoTitulo tipo,

        @NotBlank(message = "descricao é obrigatória")
        String descricao,

        @NotBlank(message = "quem é obrigatório")
        String quem,

        @NotNull(message = "vencimento é obrigatório")
        LocalDate vencimento,

        @NotNull(message = "valor é obrigatório")
        @DecimalMin(value = "0.01", message = "valor deve ser maior que zero")
        @Digits(integer = 10, fraction = 2, message = "valor deve ter no máximo 2 casas decimais")
        BigDecimal valor,

        Long vendaId
) {
}