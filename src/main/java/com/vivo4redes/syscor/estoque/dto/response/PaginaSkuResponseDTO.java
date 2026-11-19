package com.vivo4redes.syscor.estoque.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Página da conferência por SKU + totais por resultado (todas as linhas, sem filtro) e a soma de |valor estimado| das
 * diferenças.
 */
public record PaginaSkuResponseDTO(
        List<InventarioSkuResponseDTO> conteudo,
        int pagina,
        int tamanho,
        long totalItens,
        int totalPaginas,
        Map<String, Long> totaisPorResultado,
        BigDecimal valorEstimadoDiferencas,
        ResumoMb52DTO mb52
) {
}
