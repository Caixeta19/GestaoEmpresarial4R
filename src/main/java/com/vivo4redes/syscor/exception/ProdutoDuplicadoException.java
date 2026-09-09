package com.vivo4redes.syscor.exception;

public class ProdutoDuplicadoException extends BusinessException {

    public ProdutoDuplicadoException(String sku) {
        super("Já existe um produto cadastrado com o SKU: " + sku);
    }
}