package com.vivo4redes.syscor.estoque.dto;

import java.math.BigDecimal;

/** Linha de preço já extraída de uma aba da planilha da Telefônica — antes de resolver o SKU. */
public record LinhaPrecoDTO(
        String nomeComercial,
        String categoriaPlanilha,
        String ofertaComunicacao, // nulo quando o item não varia por plano
        BigDecimal preco
) {
}