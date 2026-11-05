package com.vivo4redes.syscor.venda.dto.response;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.model.PagamentoVenda;

import java.math.BigDecimal;

/** Linha de pagamento a remover antes de trocar a condição. */
public record PagamentoIncompativelDTO(Long id, FormaPagamento forma, BigDecimal valor, Integer parcelas) {

    public static PagamentoIncompativelDTO from(PagamentoVenda p) {
        return new PagamentoIncompativelDTO(p.getId(), p.getForma(), p.getValor(), p.getParcelas());
    }
}
