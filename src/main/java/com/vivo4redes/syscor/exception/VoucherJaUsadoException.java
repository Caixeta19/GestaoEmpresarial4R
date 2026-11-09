package com.vivo4redes.syscor.exception;

/** O número do voucher já está em outra venda não cancelada (vira 409). */
public class VoucherJaUsadoException extends BusinessException {

    public VoucherJaUsadoException(Long vendaId) {
        super("Voucher já usado na venda #" + vendaId);
    }
}
