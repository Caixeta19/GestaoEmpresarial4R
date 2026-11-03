package com.vivo4redes.syscor.exception;

import com.vivo4redes.syscor.venda.enums.StatusVenda;

public class CarrinhoNaoEditavelException extends BusinessException {
    public CarrinhoNaoEditavelException(Long vendaId, StatusVenda status) {
        super(status == StatusVenda.CANCELADA
                ? "A venda " + vendaId + " foi cancelada (o carrinho pode ter expirado por inatividade). Abra uma nova venda."
                : "A venda " + vendaId + " não está no status ABERTA — não é mais possível adicionar/remover itens.");
    }
}
