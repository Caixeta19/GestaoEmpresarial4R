package com.vivo4redes.syscor.exception;

/** A filial já tem um inventário ABERTO ou EM_CONTAGEM (vira 409). */
public class InventarioAbertoException extends BusinessException {

    private final Long execucaoId;

    public InventarioAbertoException(Long execucaoId) {
        super("Já existe um inventário aberto para esta filial (#" + execucaoId + "): continue a contagem ou cancele antes de abrir outro.");
        this.execucaoId = execucaoId;
    }

    public Long getExecucaoId() {
        return execucaoId;
    }
}
