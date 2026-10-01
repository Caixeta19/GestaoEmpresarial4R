package com.vivo4redes.syscor.exception;

public class ClienteDuplicadoException extends BusinessException {
    public ClienteDuplicadoException(String cpfCnpj) {
        super("Já existe um cliente cadastrado com o documento: " + cpfCnpj);
    }
}