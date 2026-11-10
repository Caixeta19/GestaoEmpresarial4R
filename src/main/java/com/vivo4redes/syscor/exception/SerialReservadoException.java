package com.vivo4redes.syscor.exception;

/** O IMEI/Serial lido já está preso no carrinho de outra venda aberta (vira 409). */
public class SerialReservadoException extends BusinessException {

    public SerialReservadoException(Long vendaId) {
        super("Serial já está reservado na venda #" + vendaId);
    }
}
