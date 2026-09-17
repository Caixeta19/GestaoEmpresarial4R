package com.vivo4redes.syscor.venda.dto.request;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PagamentoVendaRequestDTO(

        @NotNull(message = "forma é obrigatória (CARTAO_CREDITO, CARTAO_DEBITO, PIX, DINHEIRO ou BOLETO)")
        FormaPagamento forma,

        @NotNull(message = "valor é obrigatório")
        @DecimalMin(value = "0.01", message = "valor deve ser maior que zero")
        BigDecimal valor,

        @Min(value = 1, message = "parcelas deve ser pelo menos 1")
        Integer parcelas
) {
}