package com.vivo4redes.syscor.financeiro.dto.caixa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record FecharCaixaRequestDTO(
        @NotEmpty(message = "Informe o valor conferido de cada forma de pagamento")
        List<@Valid ConferenciaFormaDTO> formas
) {
}