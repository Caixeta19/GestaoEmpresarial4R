package com.vivo4redes.syscor.exception;

public class ConsentimentoInvalidoException extends BusinessException {
    public ConsentimentoInvalidoException() {
        super("versaoTermoConsentimento é obrigatória quando consentimentoMarketing é true.");
    }
}