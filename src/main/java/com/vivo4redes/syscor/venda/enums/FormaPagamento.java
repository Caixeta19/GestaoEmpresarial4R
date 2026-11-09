package com.vivo4redes.syscor.venda.enums;

public enum FormaPagamento {
    CARTAO_CREDITO,
    CARTAO_DEBITO,
    PIX,
    DINHEIRO,
    BOLETO,
    /** Só para o histórico: o voucher deixou de ser forma de pagamento (V28); o número fica em Venda.codigoVoucher. */
    VOUCHER,
    /** Na tabela da Telefônica vale como PIX. */
    VIVO_PAY
}
