package com.vivo4redes.syscor.exception;

public class RecursoNaoEncontradoException extends RuntimeException {
    public RecursoNaoEncontradoException(String model, Object id) {
        super(model + "não encontrado(a) com o id:" + id);
    }
}
