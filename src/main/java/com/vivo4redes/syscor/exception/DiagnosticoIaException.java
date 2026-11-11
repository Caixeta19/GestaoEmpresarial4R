package com.vivo4redes.syscor.exception;

/** A API do Claude falhou ou devolveu resposta inválida depois da nova tentativa (vira 502). */
public class DiagnosticoIaException extends RuntimeException {

    public DiagnosticoIaException(String mensagem) {
        super(mensagem);
    }

    public DiagnosticoIaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
