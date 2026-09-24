package com.vivo4redes.syscor.financeiro.dto.caixa;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ConferenciaFormaDTO(
        @NotNull FormaPagamento forma,
        @NotNull BigDecimal valorConferido
) {
}