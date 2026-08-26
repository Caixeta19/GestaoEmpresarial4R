package com.vivo4redes.syscor.exception;

/**
 * Mensagem propositalmente genérica (mesmo padrão do US-001): não revela se
 * o e-mail existe ou se foi a senha que errou.
 */
public class AutenticacaoInvalidaException extends BusinessException {

    public AutenticacaoInvalidaException() {
        super("E-mail ou senha do vendedor inválidos.");
    }
}