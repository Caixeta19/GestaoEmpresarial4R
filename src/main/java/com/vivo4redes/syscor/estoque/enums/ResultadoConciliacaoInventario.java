package com.vivo4redes.syscor.estoque.enums;

public enum ResultadoConciliacaoInventario {
    /** Serial existe no nosso estoque com status DISPONIVEL — bate com o SAP. */
    CORRETO,
    /** Serial existe nos dois lados, mas com estado inconsistente (ex.: já vendido aqui, ainda em depósito no SAP);
     *  ou existe aqui como DISPONIVEL mas não aparece no relatório SAP enviado. */
    DIVERGENCIA,
    /** Serial aparece no relatório SAP mas nunca foi cadastrado/deu entrada no nosso sistema. */
    PENDENTE
}
