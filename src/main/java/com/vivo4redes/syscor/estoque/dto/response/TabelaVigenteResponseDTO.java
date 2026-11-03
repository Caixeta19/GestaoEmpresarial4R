package com.vivo4redes.syscor.estoque.dto.response;

import java.time.Instant;
import java.time.LocalDate;

/** GET /produtos/precos/vigente — a execução mais recente da tabela da Telefônica. */
public record TabelaVigenteResponseDTO(
        Long execucaoId,
        LocalDate vigencia,
        Instant importadoEm,
        String nomeArquivo,
        Totais totais
) {
    public record Totais(long precos, long planos, long produtos, Integer unidadesAtualizadas, Integer planilhaSemEstoque) {
    }
}
