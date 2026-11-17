package com.vivo4redes.syscor.financeiro.relatorio.service;

import com.vivo4redes.syscor.financeiro.relatorio.dto.DetalhePagamentoDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Conteúdo dos arquivos exportados: a lista plana de pagamentos (ordenada por nº da venda) e os totais calculados
 * dessa mesma lista, para que a soma da coluna Valor, a soma por forma e o total geral batam por construção.
 */
record ExportacaoFormasPagamento(LocalDate de, LocalDate ate, OffsetDateTime geradoEm, long semDataFinalizacao,
                                 List<DetalhePagamentoDTO> linhas, List<TotalForma> totaisPorForma,
                                 BigDecimal totalGeral, long quantidadePagamentos, long quantidadeVendas) {

    record TotalForma(String formaRotulo, long quantidade, BigDecimal valor, BigDecimal percentual) {
    }
}
