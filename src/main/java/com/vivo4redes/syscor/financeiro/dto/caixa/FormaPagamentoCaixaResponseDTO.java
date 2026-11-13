package com.vivo4redes.syscor.financeiro.dto.caixa;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;

import java.math.BigDecimal;

/**
 * Uma forma do caixa. Com o caixa ABERTO, quem não vê o esperado (gerente da loja) recebe {@code null} em movimento,
 * entradas, saídas, contabilizadoEsperado e esperadoConferencia de TODAS as formas (somados, dariam o esperado); a
 * abertura ele mesmo informou. Depois de fechado todos veem.
 */
public record FormaPagamentoCaixaResponseDTO(
        Long id,
        FormaPagamento forma,
        BigDecimal abertura,
        /** Suprimentos efetivos lançados no caixa (só dinheiro). */
        BigDecimal movimento,
        /** Soma dos pagamentos das vendas CONCLUIDAS lançadas neste caixa (cancelada não entra). */
        BigDecimal entradas,
        /** Sangrias efetivas lançadas no caixa (só dinheiro). */
        BigDecimal saidas,
        /** O que fica no caixa: o esperado do dinheiro; 0,00 nas formas que liquidam na conciliação. */
        BigDecimal contabilizadoEsperado,
        /** Contra o que o valor digitado é conferido: abertura + movimento + entradas − saídas, em todas as formas. */
        BigDecimal esperadoConferencia,
        /** O valor digitado no fechamento; só depois de fechado. */
        BigDecimal valorConferido,
        /** valorConferido − esperadoConferencia; só depois de fechado. */
        BigDecimal diferenca,
        /** true = PIX, Vivo Pay, cartões e boleto: o total recebido é conferido contra o extrato ou a maquininha. */
        boolean liquidaNaConciliacao,
        /** true = precisa ser informada para fechar: o dinheiro sempre, e as formas com abertura ou movimento. */
        boolean exigidaNoFechamento
) {
}
