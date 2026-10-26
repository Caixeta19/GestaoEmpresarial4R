package com.vivo4redes.syscor.estoque.dto.response;

import java.math.BigDecimal;

/**
 * Saldo agregado por modelo. A criticidade segue a regra do README:
 * crítico quando {@code disponivel <= estoqueMinimo} (padrão 2).
 */
public record EstoqueConsolidadoModeloDTO(
        String modeloComercial,
        String marca,
        String categoria,
        Long disponivel,
        Long reservado,
        Long avariado,
        BigDecimal valorVendaMinimo
) {
    public static final int ESTOQUE_MINIMO_PADRAO = 2;

    public String situacao() {
        long saldo = disponivel == null ? 0 : disponivel;
        if (saldo <= 0) return "Ruptura";
        if (saldo <= ESTOQUE_MINIMO_PADRAO) return "Crítico";
        return "Disponível";
    }

    public boolean critico() {
        return (disponivel == null ? 0 : disponivel) <= ESTOQUE_MINIMO_PADRAO;
    }
}