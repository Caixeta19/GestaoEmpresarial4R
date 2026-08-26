package com.vivo4redes.syscor.exception;

public class CarrinhoNaoEditavelException extends BusinessException {
    public CarrinhoNaoEditavelException(Long vendaId) {
        super("A venda " + vendaId + " não está no status ABERTA — não é mais possível adicionar/remover itens.");
    }
}
