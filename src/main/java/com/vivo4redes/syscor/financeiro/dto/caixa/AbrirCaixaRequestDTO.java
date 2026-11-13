package com.vivo4redes.syscor.financeiro.dto.caixa;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * A data do caixa é sempre hoje (America/Sao_Paulo); o cliente não escolhe. As formas que não vierem abrem com 0.
 * {@code justificativaAbertura} é obrigatória (mín. 10 caracteres) quando o dinheiro não abre com o valor sugerido
 * (fechamento do último caixa desse caixa).
 */
public record AbrirCaixaRequestDTO(
        @NotNull(message = "filialId é obrigatório")
        Long filialId,

        @NotBlank(message = "caixaPdv é obrigatório")
        String caixaPdv,

        List<@Valid AberturaFormaDTO> formasIniciais,

        String justificativaAbertura
) {
}
