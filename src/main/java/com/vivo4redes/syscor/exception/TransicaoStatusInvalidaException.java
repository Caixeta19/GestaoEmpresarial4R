package com.vivo4redes.syscor.exception;

import com.vivo4redes.syscor.enums.StatusVenda;

public class TransicaoStatusInvalidaException extends RuntimeException {
  public TransicaoStatusInvalidaException(StatusVenda de, StatusVenda para) {
    super("Transição de status não permitida:" + de + "->" + para);
  }
}
