package com.vivo4redes.syscor.financeiro.dto.response;

import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TituloPagarDTO(Long id, String descricao, String fornecedor, LocalDate vencimento, BigDecimal valor,
                             String status, String banco, OffsetDateTime dataPagamento, BigDecimal valorPago,
                             String comprovanteUrl) {

    /** >>> AJUSTE os getters marcados aos nomes reais do seu TituloFinanceiro. */
    public static TituloPagarDTO de(TituloFinanceiro t) {
        return new TituloPagarDTO(
                t.getId(),
                t.getDescricao(),          // ajustar
                t.getFornecedor(),         // ajustar (pode ser getParceiro(), getNomeFornecedor()...)
                t.getVencimento(),         // ajustar (pode ser getDataVencimento())
                t.getValor(),              // ajustar
                t.getStatus().name(),      // ajustar
                t.getBancoPagador(),
                t.getPagoEm(),
                t.getValorPago(),
                t.getComprovanteUrl());
    }
}