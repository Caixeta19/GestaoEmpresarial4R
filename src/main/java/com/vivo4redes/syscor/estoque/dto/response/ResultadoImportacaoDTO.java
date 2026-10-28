package com.vivo4redes.syscor.estoque.dto.response;

import java.util.List;

/**
 * Resumo de uma importação (relatório de estoque ou atualização de preços).
 * Linhas inválidas não abortam o lote: são listadas em {@code erros} (máx. 100).
 */
public record ResultadoImportacaoDTO(
        int totalLinhas,
        int inseridos,
        int atualizados,
        int ignorados,
        List<String> erros
) {
    private static final int MAX_ERROS = 100;

    public ResultadoImportacaoDTO {
        erros = erros == null ? List.of()
                : erros.size() > MAX_ERROS ? List.copyOf(erros.subList(0, MAX_ERROS))
                : List.copyOf(erros);
    }
}