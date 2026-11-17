package com.vivo4redes.syscor.financeiro.relatorio.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Relatório de formas de pagamento calculado ao vivo das vendas CONCLUIDA. {@code semDataFinalizacao}: vendas
 * CONCLUIDA (no escopo do usuário) sem {@code finalizadoEm}, que ficam fora porque não têm dia de referência.
 */
public record RelatorioFormasPagamentoDTO(Periodo periodo, AgruparPor agruparPor, OffsetDateTime geradoEm,
                                          long semDataFinalizacao, List<Grupo> grupos, BigDecimal totalGeral,
                                          long quantidadePagamentos, long quantidadeVendas,
                                          List<TotalForma> totaisPorForma) {

    public record Periodo(LocalDate de, LocalDate ate) {
    }

    public record Grupo(String chave, String rotulo, BigDecimal total, long quantidadePagamentos,
                        long quantidadeVendas, List<Linha> linhas) {
    }

    /** Linha de um grupo: por forma (grupo por filial/caixa) ou por filial (grupo por forma). */
    public record Linha(String chave, String rotulo, String forma, String formaRotulo, BigDecimal valor,
                        BigDecimal percentual, long quantidade) {
    }

    public record TotalForma(String forma, String formaRotulo, BigDecimal valor, BigDecimal percentual) {
    }
}
