package com.vivo4redes.syscor.financeiro.dto.caixa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** {@code observacao} é obrigatória (mín. 10 caracteres) quando alguma forma fecha com diferença. */
public record FecharCaixaRequestDTO(
        @NotEmpty(message = "Informe o valor conferido de cada forma de pagamento")
        List<@Valid ConferenciaFormaDTO> formas,

        String observacao
) {
}
