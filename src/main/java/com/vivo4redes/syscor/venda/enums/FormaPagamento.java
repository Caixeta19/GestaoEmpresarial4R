package com.vivo4redes.syscor.venda.enums;

public enum FormaPagamento {
    CARTAO_CREDITO,
    CARTAO_DEBITO,
    PIX,
    DINHEIRO,
    BOLETO,
    /** Voucher do fabricante (com código). Junto com o Desconto fabricante, liga a TABELA RENOVA. */
    VOUCHER,
    /** Na tabela da Telefônica vale como PIX. */
    VIVO_PAY
}
