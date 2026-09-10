package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.model.InventarioExecucao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/** Alimenta a tela de Inventário: os 3 cards (corretos/divergência/pendentes) + o detalhe linha a linha. */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventarioResumoResponseDTO {
    private Long execucaoId;
    private String nomeArquivo;
    private Instant dataExecucao;
    private Integer totalLinhasSap;
    private Integer totalCorretos;
    private Integer totalDivergencias;
    private Integer totalPendentes;
    private List<InventarioItemResponseDTO> itens;

    public static InventarioResumoResponseDTO from(InventarioExecucao e, List<InventarioItemResponseDTO> itens) {
        return InventarioResumoResponseDTO.builder()
                .execucaoId(e.getId())
                .nomeArquivo(e.getNomeArquivo())
                .dataExecucao(e.getCriadoEm())
                .totalLinhasSap(e.getTotalLinhasSap())
                .totalCorretos(e.getTotalCorretos())
                .totalDivergencias(e.getTotalDivergencias())
                .totalPendentes(e.getTotalPendentes())
                .itens(itens)
                .build();
    }
}