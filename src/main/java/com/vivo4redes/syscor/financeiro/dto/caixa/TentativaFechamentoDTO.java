package com.vivo4redes.syscor.financeiro.dto.caixa;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Fechamento recusado por diferença, com os valores; só quem confere ou pode fechar com divergência recebe. */
public record TentativaFechamentoDTO(Instant em, String usuario, List<Linha> formas) {

    public record Linha(FormaPagamento forma, BigDecimal digitado, BigDecimal esperado, BigDecimal diferenca) {
    }
}
