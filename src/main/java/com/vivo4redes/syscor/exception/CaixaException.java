package com.vivo4redes.syscor.exception;

import org.springframework.http.HttpStatus;

/**
 * Erro do caixa com código estável para o front tratar (CAIXA_NAO_ABERTO, CAIXA_JA_ABERTO, CAIXA_FECHADO,
 * CAIXA_SEM_PERMISSAO...). O handler devolve o status informado e o {@code codigo} no corpo.
 */
public class CaixaException extends BusinessException {

    private final String codigo;
    private final HttpStatus status;

    public CaixaException(String codigo, HttpStatus status, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
        this.status = status;
    }

    public static CaixaException naoAberto() {
        return new CaixaException("CAIXA_NAO_ABERTO", HttpStatus.CONFLICT,
                "O caixa de hoje ainda não foi aberto. Peça ao gerente para abrir o caixa antes de lançar vendas.");
    }

    public static CaixaException jaAberto(String caixaPdv) {
        return new CaixaException("CAIXA_JA_ABERTO", HttpStatus.CONFLICT,
                "O caixa \"" + caixaPdv + "\" já foi aberto hoje nessa filial. Para mexer nele, use o caixa existente.");
    }

    public static CaixaException fechado(String mensagem) {
        return new CaixaException("CAIXA_FECHADO", HttpStatus.CONFLICT, mensagem);
    }

    public static CaixaException semPermissao(String mensagem) {
        return new CaixaException("CAIXA_SEM_PERMISSAO", HttpStatus.FORBIDDEN, mensagem);
    }

    public String getCodigo() {
        return codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
