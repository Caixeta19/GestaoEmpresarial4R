package com.vivo4redes.syscor.financeiro.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Confirma à mão que o lançamento pagou a conta. O valor do banco manda; se difere do valor da conta, a
 * {@code justificativa} (mín. 10 caracteres) é obrigatória.
 */
public record VincularLancamentoRequestDTO(
        @NotNull(message = "tituloId é obrigatório")
        Long tituloId,

        @Size(max = 300, message = "justificativa aceita até 300 caracteres")
        String justificativa
) {
}
