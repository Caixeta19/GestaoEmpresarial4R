package com.vivo4redes.syscor.estoque.enums;

/** Ciclo da execução de inventário: ABERTO -> EM_CONTAGEM (1ª bipagem) -> FECHADO, ou CANCELADO. */
public enum StatusInventario {
    ABERTO,
    EM_CONTAGEM,
    FECHADO,
    CANCELADO;

    public boolean emAndamento() {
        return this == ABERTO || this == EM_CONTAGEM;
    }
}
