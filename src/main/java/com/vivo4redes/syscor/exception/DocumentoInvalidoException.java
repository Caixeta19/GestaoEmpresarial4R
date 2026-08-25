package com.vivo4redes.syscor.exception;

public class DocumentoInvalidoException extends RuntimeException {
    public DocumentoInvalidoException(String documento) {
        super("CPF/CNPJ inválido:" + documento);
    }
}
