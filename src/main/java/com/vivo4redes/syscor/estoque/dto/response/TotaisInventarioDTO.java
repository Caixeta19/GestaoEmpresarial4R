package com.vivo4redes.syscor.estoque.dto.response;

import java.util.Map;

/**
 * Contadores da tela de contagem. {@code esperados} = IQ09 da loja ∪ estoque da loja; {@code porResultado} traz todos
 * os resultados, inclusive os zerados.
 */
public record TotaisInventarioDTO(
        long esperados,
        long bipados,
        long pendentes,
        long conferidos,
        long sobras,
        long divergencias,
        Map<String, Long> porResultado
) {
}
