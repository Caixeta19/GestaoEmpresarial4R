package com.vivo4redes.syscor.estoque.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

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
        Long defeito,
        BigDecimal valorVendaMinimo
) {
    public static final int ESTOQUE_MINIMO_PADRAO = 2;

    /**
     * Alias temporário de {@link #defeito()} para não quebrar o syscor-frontend,
     * que ainda lê a chave "avariado". Remover quando o front passar a usar "defeito".
     */
    @Deprecated
    @JsonProperty("avariado")
    public Long avariado() {
        return defeito;
    }

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