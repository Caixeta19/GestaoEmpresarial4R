package com.vivo4redes.syscor.financeiro.dto.caixa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record AbrirCaixaRequestDTO(
        @NotNull(message = "filialId é obrigatório")
        Long filialId,

        @NotBlank(message = "caixaPdv é obrigatório")
        String caixaPdv,

        @NotEmpty(message = "Informe ao menos uma forma de pagamento com o valor de abertura")
        List<@Valid AberturaFormaDTO> formasIniciais
) {
}