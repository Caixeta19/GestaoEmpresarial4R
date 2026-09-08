package com.vivo4redes.syscor.exception;

public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String model) {
        super(model + "não encontrado(a) com o id:" + id);
    }
}
