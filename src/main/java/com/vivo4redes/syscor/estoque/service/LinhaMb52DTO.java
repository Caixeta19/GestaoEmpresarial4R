package com.vivo4redes.syscor.estoque.service;

import java.math.BigDecimal;

/**
 * Uma linha de dados da MB52 (estoque do SAP por material x centro x depósito). Quantidades já com sinal; centro e
 * depósito em maiúsculas. {@code valorLivre} nulo quando a coluna não veio.
 */
public record LinhaMb52DTO(
        int numeroLinha,
        String material,
        String denominacao,
        String centro,
        String deposito,
        String unidade,
        BigDecimal livre,
        BigDecimal qualidade,
        BigDecimal bloqueado,
        BigDecimal transito,
        BigDecimal valorLivre
) {

    /** Resultado da leitura: linhas de dados válidas + quantas foram ignoradas (total, subtotal, cabeçalho repetido). */
    public record Leitura(java.util.List<LinhaMb52DTO> linhas, int ignoradas) {
        public int total() {
            return linhas.size() + ignoradas;
        }
    }
}
