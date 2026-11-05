package com.vivo4redes.syscor.estoque.dto.response;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Relatório do upload da tabela da Telefônica.
 * <p>
 * Os campos {@code nomeArquivo}, {@code vigenciaEm}, {@code dataExecucao}, {@code totalLinhasProcessadas},
 * {@code totalProdutosAtualizados} e {@code totalNaoEncontrados} são os do formato antigo
 * (TabelaPrecoResumoResponseDTO), mantidos enquanto o syscor-frontend não migra.
 */
public record ImportacaoTabelaPrecoResponseDTO(
        Long execucaoId,
        LocalDate vigencia,
        Map<String, Integer> linhasPorAba,
        int totalPrecos,
        int totalPlanos,
        int totalProdutos,
        int unidadesAtualizadas,
        List<String> estoqueSemPreco,
        int planilhaSemEstoque,
        /** PIX e 13x–21x da planilha que diferem da fórmula em mais de R$ 0,01 (a planilha prevalece). */
        List<DivergenciaCondicaoDTO> divergenciasCondicao,
        // ---- formato antigo (deprecado)
        String nomeArquivo,
        String vigenciaEm,
        Instant dataExecucao,
        Integer totalLinhasProcessadas,
        Integer totalProdutosAtualizados,
        Integer totalNaoEncontrados
) {
}
