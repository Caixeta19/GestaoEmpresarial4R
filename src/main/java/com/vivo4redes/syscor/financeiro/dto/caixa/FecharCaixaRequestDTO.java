package com.vivo4redes.syscor.financeiro.dto.caixa;

import jakarta.validation.Valid;

import java.util.List;

/**
 * Só o dinheiro é contado e é obrigatório: sem ele (ou com a lista vazia) o fechamento é recusado com 422, e nenhuma
 * regra assume o esperado como valor contado. {@code observacao} é obrigatória (mín. 10 caracteres) quando alguma forma fecha com diferença. */
public record FecharCaixaRequestDTO(
        List<@Valid ConferenciaFormaDTO> formas,

        String observacao
) {
}
