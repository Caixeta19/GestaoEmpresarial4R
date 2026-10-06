package com.vivo4redes.syscor.financeiro.dto.response;

import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TituloPagarDTO(Long id, String descricao, String fornecedor, LocalDate vencimento, BigDecimal valor,
                             String status, String banco, OffsetDateTime dataPagamento, BigDecimal valorPago,
                             String comprovanteUrl) {

    public static TituloPagarDTO de(TituloFinanceiro t) {
        return new TituloPagarDTO(
                t.getId(),
                t.getDescricao(),
                t.getQuem(),               // "quem" guarda o fornecedor nos títulos a pagar
                t.getVencimento(),
                t.getValor(),
                t.getStatus().name(),
                t.getBancoPagador(),
                t.getPagoEm(),
                t.getValorPago(),
                t.getComprovanteUrl());
    }
}