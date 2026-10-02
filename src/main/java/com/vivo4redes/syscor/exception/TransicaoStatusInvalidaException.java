package com.vivo4redes.syscor.exception;

import com.vivo4redes.syscor.venda.enums.StatusVenda;

public class TransicaoStatusInvalidaException extends BusinessException {
  public TransicaoStatusInvalidaException(StatusVenda de, StatusVenda para) {
    super("Transição de status não permitida: " + de + " -> " + para);
  }
}