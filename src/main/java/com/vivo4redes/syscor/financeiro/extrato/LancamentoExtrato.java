package com.vivo4redes.syscor.financeiro.extrato;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Débito contabilizado do extrato, já no vocabulário do SYSCOR (nenhum tipo do banco vaza para fora do adaptador).
 * {@code payloadRaw} é o item como o banco enviou, sem credenciais.
 */
public record LancamentoExtrato(
        String idExterno,
        LocalDate dataLancamento,
        BigDecimal valor,
        Long numeroDocumento,
        Integer numeroLote,
        Integer codigoHistorico,
        String subHistorico,
        String infoComplementar,
        String contraparteDocumento,
        String contraparteTipo,
        Integer contraparteBanco,
        String payloadRaw) {
}
