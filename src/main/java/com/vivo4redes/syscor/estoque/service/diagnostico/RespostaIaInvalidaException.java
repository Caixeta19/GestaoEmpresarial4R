package com.vivo4redes.syscor.estoque.service.diagnostico;

/** A IA respondeu, mas fora do formato esperado (ou recusou): vale uma nova tentativa. */
public class RespostaIaInvalidaException extends RuntimeException {

    public RespostaIaInvalidaException(String mensagem) {
        super(mensagem);
    }

    public RespostaIaInvalidaException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
