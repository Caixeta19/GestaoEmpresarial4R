package com.vivo4redes.syscor.relatorio.dto;

import java.util.List;
import java.util.Map;

/**
 * Página da tabela na tela. {@code colunas} dá a ordem e o rótulo; cada item de {@code content} é um mapa
 * chave → valor. {@code totais} traz os subtotais por grupo de todo o filtro (só em Produtos agrupado, no modo
 * analítico).
 */
public record PaginaRelatorioDTO(
        List<Coluna> colunas,
        List<Map<String, Object>> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        List<Map<String, Object>> totais
) {
    public record Coluna(String chave, String rotulo, CampoRelatorio.Formato formato) {
    }
}
