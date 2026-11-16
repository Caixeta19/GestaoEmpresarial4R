package com.vivo4redes.syscor.financeiro.extrato;

/**
 * Falha ao consultar o extrato do banco. A mensagem nunca carrega URL, chave de aplicação, token, agência ou conta:
 * ela vai para a tela e para a tabela de sincronização.
 */
public class ExtratoBancarioException extends RuntimeException {

    public ExtratoBancarioException(String mensagem) {
        super(mensagem);
    }

    public ExtratoBancarioException(String mensagem, Throwable causa) {
        super(mensagem, causa);
    }
}
