package com.vivo4redes.syscor.financeiro.relatorio.service;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;

/** Rótulos em português das formas de pagamento para telas e arquivos do relatório. */
final class FormaPagamentoRotulos {

    private FormaPagamentoRotulos() {
    }

    static String rotulo(FormaPagamento forma) {
        return switch (forma) {
            case CARTAO_CREDITO -> "Cartão de crédito";
            case CARTAO_DEBITO -> "Cartão de débito";
            case PIX -> "PIX";
            case DINHEIRO -> "Dinheiro";
            case BOLETO -> "Boleto";
            case VOUCHER -> "Voucher (histórico)";
            case VIVO_PAY -> "Vivo Pay";
        };
    }
}
