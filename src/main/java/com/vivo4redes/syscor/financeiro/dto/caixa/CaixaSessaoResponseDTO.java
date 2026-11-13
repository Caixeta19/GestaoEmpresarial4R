package com.vivo4redes.syscor.financeiro.dto.caixa;

import com.vivo4redes.syscor.financeiro.enums.ConferenciaCaixa;
import com.vivo4redes.syscor.financeiro.enums.StatusCaixaSessao;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CaixaSessaoResponseDTO(
        Long id,
        Long filialId,
        String filialNome,
        String caixaPdv,
        LocalDate dataAbertura,
        StatusCaixaSessao status,
        ConferenciaCaixa conferencia,
        Instant abertoEm,
        String abertoPor,
        Instant fechadoEm,
        String fechadoPor,
        Instant conferidoEm,
        String conferidoPor,
        Instant reabertoEm,
        String reabertoPor,
        String observacao,
        String justificativaAbertura,
        String justificativaReabertura,
        /** Soma da abertura de todas as formas. */
        BigDecimal totalAbertura,
        /** Soma do valor conferido (contabilizado) de todas as formas; null com o caixa aberto. */
        BigDecimal totalContabilizado,
        /** Soma das diferenças; null com o caixa aberto. */
        BigDecimal totalDiferenca,
        List<FormaPagamentoCaixaResponseDTO> formas,
        /** Só no detalhe (GET /{id}); nas listas vem null. */
        List<HistoricoCaixaDTO> historico
) {
}
