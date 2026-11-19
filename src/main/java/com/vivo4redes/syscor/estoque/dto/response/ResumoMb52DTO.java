package com.vivo4redes.syscor.estoque.dto.response;

import java.time.Instant;
import java.util.Map;

/**
 * MB52 da execução no resumo do inventário. {@code totaisPorResultado} só vem no detalhe ({@code GET /{id}}); na
 * listagem é nulo.
 */
public record ResumoMb52DTO(
        boolean importada,
        String arquivo,
        Instant em,
        String por,
        int totalLinhas,
        int linhasDaFilial,
        int linhasOutroCentro,
        int linhasIgnoradas,
        Map<String, Long> totaisPorResultado
) {

    public static final ResumoMb52DTO NAO_IMPORTADA = new ResumoMb52DTO(false, null, null, null, 0, 0, 0, 0, null);
}
