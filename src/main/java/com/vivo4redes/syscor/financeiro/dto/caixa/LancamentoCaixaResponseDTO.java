package com.vivo4redes.syscor.financeiro.dto.caixa;

import com.vivo4redes.syscor.financeiro.enums.TipoLancamentoCaixa;

import java.math.BigDecimal;
import java.time.Instant;

public record LancamentoCaixaResponseDTO(
        Long id,
        TipoLancamentoCaixa tipo,
        BigDecimal valor,
        String descricao,
        String conta,
        String observacao,
        /** Só no estorno. */
        String justificativa,
        /** Só no estorno: o lançamento que ele desfaz. */
        Long estornadoDeId,
        /** true = esse lançamento já foi desfeito por um estorno. */
        boolean estornado,
        String criadoPor,
        Instant criadoEm
) {
}
