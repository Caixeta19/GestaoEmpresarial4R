package com.vivo4redes.syscor.financeiro.dto.caixa;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.enums.StatusVenda;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma linha do "Movimento detalhado do caixa": cada pagamento lançado e cada sangria, suprimento e estorno. Nas linhas de
 * lançamento, {@code vendaId}, {@code numeroVenda} e {@code statusVenda} vêm nulos e {@code vendedor} é quem lançou.
 */
public record MovimentoCaixaResponseDTO(
        /** PAGAMENTO, SANGRIA, SUPRIMENTO ou ESTORNO. */
        String tipo,
        Long vendaId,
        Long numeroVenda,
        Instant hora,
        String vendedor,
        FormaPagamento forma,
        BigDecimal valor,
        StatusVenda statusVenda,
        /** Conta de destino ou origem do lançamento. */
        String conta,
        String descricao
) {
}
