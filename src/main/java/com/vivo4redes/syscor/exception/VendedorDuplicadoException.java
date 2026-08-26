package com.vivo4redes.syscor.exception;

public class VendedorDuplicadoException extends BusinessException {

  public VendedorDuplicadoException(String email) {
    super("Já existe vendedor cadastrado com o e-mail: " + email);
  }
}
