package com.vivo4redes.syscor.financeiro.dto.response;

import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Conta a pagar na lista e no detalhe. Os 10 primeiros campos são o contrato original; os demais só foram somados. */
public record TituloPagarDTO(Long id, String descricao, String fornecedor, LocalDate vencimento, BigDecimal valor,
                             String status, String banco, OffsetDateTime dataPagamento, BigDecimal valorPago,
                             String comprovanteUrl,
                             String documentoFornecedor, String formaPagamento, String codigoBarras, Long filialId,
                             String categoria, String observacao, String origemBaixa, boolean temComprovante,
                             boolean vencida) {

    public static TituloPagarDTO de(TituloFinanceiro t) {
        boolean vencida = t.getStatus() == StatusTitulo.ABERTO && t.getVencimento().isBefore(LocalDate.now());
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
                t.getComprovanteUrl(),
                t.getDocumentoFornecedor(),
                t.getFormaPagamento() == null ? null : t.getFormaPagamento().name(),
                t.getCodigoBarras(),
                t.getFilialId(),
                t.getCategoria(),
                t.getObservacao(),
                t.getOrigemBaixa() == null ? null : t.getOrigemBaixa().name(),
                t.getStatus() == StatusTitulo.BAIXADO,
                vencida);
    }
}
