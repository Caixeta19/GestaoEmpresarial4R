package com.vivo4redes.syscor.exception;

public class VendaSemItemException extends BusinessException {

    public VendaSemItemException() {
        super("Não é possível registrar uma venda sem itens");
    }
}
