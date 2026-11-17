package com.vivo4redes.syscor.exception;

import org.springframework.http.HttpStatus;

/**
 * Erro de contas a pagar com código estável para o front (FINANCEIRO_SEM_PERMISSAO, CONTA_DUPLICADA,
 * CONTA_PROVAVEL_DUPLICADA, CONTA_NAO_BAIXADA...). O handler devolve o status informado e o {@code codigo} no corpo.
 */
public class FinanceiroException extends BusinessException {

    private final String codigo;
    private final HttpStatus status;

    public FinanceiroException(String codigo, HttpStatus status, String mensagem) {
        super(mensagem);
        this.codigo = codigo;
        this.status = status;
    }

    public static FinanceiroException semPermissao() {
        return new FinanceiroException("FINANCEIRO_SEM_PERMISSAO", HttpStatus.FORBIDDEN,
                "Você não tem permissão para essa ação em contas a pagar.");
    }

    public static FinanceiroException semPermissaoRelatorio() {
        return new FinanceiroException("RELATORIO_SEM_PERMISSAO", HttpStatus.FORBIDDEN,
                "Você não tem permissão para consultar os relatórios financeiros.");
    }

    public static FinanceiroException contaDuplicada() {
        return new FinanceiroException("CONTA_DUPLICADA", HttpStatus.CONFLICT,
                "Já existe uma conta não cancelada com esse código de barras.");
    }

    public static FinanceiroException contaProvavelDuplicada() {
        return new FinanceiroException("CONTA_PROVAVEL_DUPLICADA", HttpStatus.CONFLICT,
                "Já existe uma conta com o mesmo fornecedor, valor e vencimento. Confirme para cadastrar mesmo assim.");
    }

    public static FinanceiroException naoBaixada() {
        return new FinanceiroException("CONTA_NAO_BAIXADA", HttpStatus.CONFLICT,
                "O comprovante só existe para conta baixada.");
    }

    public static FinanceiroException statusInvalido(String mensagem) {
        return new FinanceiroException("CONTA_STATUS_INVALIDO", HttpStatus.CONFLICT, mensagem);
    }

    public String getCodigo() {
        return codigo;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
