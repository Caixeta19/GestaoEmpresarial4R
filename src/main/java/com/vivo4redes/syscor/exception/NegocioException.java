package com.vivo4redes.syscor.exception;

public class NegocioException extends BusinessException {

    public NegocioException(String message) {
        super(message);
    }

    public NegocioException(String message, Throwable cause) {
        super(message, cause);
    }
}