package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.model.TabelaPrecoExecucao;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TabelaPrecoResumoResponseDTO {
    private Long execucaoId;
    private String nomeArquivo;
    private String vigenciaEm;
    private Instant dataExecucao;
    private Integer totalLinhasProcessadas;
    private Integer totalProdutosAtualizados;
    private Integer totalNaoEncontrados;

    public static TabelaPrecoResumoResponseDTO from(TabelaPrecoExecucao e) {
        return TabelaPrecoResumoResponseDTO.builder()
                .execucaoId(e.getId())
                .nomeArquivo(e.getNomeArquivo())
                .vigenciaEm(e.getVigenciaEm())
                .dataExecucao(e.getCriadoEm())
                .totalLinhasProcessadas(e.getTotalLinhasProcessadas())
                .totalProdutosAtualizados(e.getTotalProdutosAtualizados())
                .totalNaoEncontrados(e.getTotalNaoEncontrados())
                .build();
    }
}