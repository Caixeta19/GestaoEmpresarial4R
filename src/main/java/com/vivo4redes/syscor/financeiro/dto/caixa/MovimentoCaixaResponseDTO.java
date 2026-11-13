package com.vivo4redes.syscor.financeiro.dto.caixa;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.enums.StatusVenda;

import java.math.BigDecimal;
import java.time.Instant;

/** Uma linha por pagamento lançado neste caixa ("Movimento detalhado do caixa"). */
public record MovimentoCaixaResponseDTO(
        Long vendaId,
        Long numeroVenda,
        Instant hora,
        String vendedor,
        FormaPagamento forma,
        BigDecimal valor,
        StatusVenda statusVenda
) {
}
