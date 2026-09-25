package com.vivo4redes.syscor.financeiro.dto.response;

import com.vivo4redes.syscor.financeiro.model.ExecucaoRemuneracao;

import java.time.Instant;
import java.util.List;

public record ExecucaoRemuneracaoResponseDTO(
        Long execucaoId,
        String nomeArquivo,
        Instant dataExecucao,
        Integer totalLinhas,
        Integer totalLiberados,
        Integer totalGlosados,
        Integer totalPendentesVivo,
        List<ItemRemuneracaoResponseDTO> itens
) {
    public static ExecucaoRemuneracaoResponseDTO from(ExecucaoRemuneracao e, List<ItemRemuneracaoResponseDTO> itens) {
        return new ExecucaoRemuneracaoResponseDTO(
                e.getId(), e.getNomeArquivo(), e.getCriadoEm(),
                e.getTotalLinhas(), e.getTotalLiberados(), e.getTotalGlosados(), e.getTotalPendentesVivo(),
                itens
        );
    }
}