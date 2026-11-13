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
        /** Fechado com diferença, por quem pode (a lista mostra a diferença em vermelho). */
        boolean fechadoComDivergencia,
        String justificativaDivergencia,
        /** Fechamentos que ainda podem ser recusados hoje para o gerente da loja (5 por caixa por dia); só no caixa aberto. */
        Integer tentativasRestantes,
        /** Soma da abertura de todas as formas. */
        BigDecimal totalAbertura,
        /** O que fica no caixa depois de fechado: o dinheiro contado (as formas que liquidam contam 0); null com o caixa aberto. */
        BigDecimal totalContabilizado,
        /** Soma das diferenças de todas as formas; null com o caixa aberto. */
        BigDecimal totalDiferenca,
        List<FormaPagamentoCaixaResponseDTO> formas,
        /** Só no detalhe (GET /{id}); nas listas vem null. */
        List<HistoricoCaixaDTO> historico,
        /** Só no detalhe e só para quem confere ou pode fechar com divergência; senão null. */
        List<TentativaFechamentoDTO> tentativasFechamento
) {
}
