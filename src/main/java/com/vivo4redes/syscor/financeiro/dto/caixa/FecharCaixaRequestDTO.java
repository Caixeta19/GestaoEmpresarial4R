package com.vivo4redes.syscor.financeiro.dto.caixa;

import jakarta.validation.Valid;

import java.util.List;

/**
 * Valor digitado de cada forma exigida (o dinheiro sempre; as demais, se tiverem abertura ou movimento). Sem ele (ou com
 * a lista vazia, ou sem a lista) o fechamento é recusado com 422, e nenhuma regra assume o esperado como valor contado.
 * O caixa só fecha com diferença zero; {@code justificativa} (mín. 10 caracteres) só vale para quem pode fechar com
 * divergência. {@code observacao} é livre e opcional.
 */
public record FecharCaixaRequestDTO(
        List<@Valid ConferenciaFormaDTO> formas,

        String observacao,

        String justificativa
) {
}
