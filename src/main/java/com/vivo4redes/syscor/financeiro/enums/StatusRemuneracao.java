package com.vivo4redes.syscor.financeiro.enums;


public enum StatusRemuneracao {
    /** Consta no consolidado da Vivo E tem protocolo/GED válido — comissão liberada. */
    LIBERADO,
    /** Consta no consolidado da Vivo mas SEM VENDA no sistema — comissão bloqueada. */
    PENDENTE_SISTEMA,
    /** Tem protocolo/GED válido mas NÃO consta no consolidado da Vivo — documentado, aguardando repasse. */
    PENDENTE_VIVO
}