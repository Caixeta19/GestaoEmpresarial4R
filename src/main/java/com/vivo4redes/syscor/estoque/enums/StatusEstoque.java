package com.vivo4redes.syscor.estoque.enums;

/** Estado operacional de uma unidade em {@code tb_estoque_geral}. */
public enum StatusEstoque {
    /** Apta para venda no PDV (estado inicial de todo item). */
    DISPONIVEL,
    /** Baixada em uma venda. */
    VENDIDO,
    /** Separada para uma venda/cliente, ainda não baixada. */
    RESERVADO,
    /** Com defeito/dano: não pode ser vendida. */
    DEFEITO
}