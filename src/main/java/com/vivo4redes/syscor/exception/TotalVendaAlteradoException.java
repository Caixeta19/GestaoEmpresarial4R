package com.vivo4redes.syscor.exception;

import java.math.BigDecimal;

/**
 * Ao finalizar, a venda reprecificada ficou com outro total. A transação confirma mesmo com a exceção, para a tela
 * receber o total novo.
 */
public class TotalVendaAlteradoException extends BusinessException {

    public TotalVendaAlteradoException(BigDecimal anterior, BigDecimal novo) {
        super("O preço da venda mudou: o total passou de " + anterior + " para " + novo
                + ". Confira os pagamentos e finalize de novo.");
    }
}
