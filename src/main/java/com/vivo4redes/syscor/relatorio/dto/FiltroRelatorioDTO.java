package com.vivo4redes.syscor.relatorio.dto;

import com.vivo4redes.syscor.relatorio.enums.OpcoesFiltro.AgruparPor;
import com.vivo4redes.syscor.relatorio.enums.OpcoesFiltro.Considerar;
import com.vivo4redes.syscor.relatorio.enums.OpcoesFiltro.Modo;
import com.vivo4redes.syscor.relatorio.enums.OpcoesFiltro.StatusVendaFiltro;
import com.vivo4redes.syscor.relatorio.enums.SegmentoComercial;
import com.vivo4redes.syscor.relatorio.enums.TipoProduto;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;

import java.time.LocalDate;
import java.util.List;

/**
 * Filtros da busca, da contagem e da exportação. Nulo ou lista vazia = não filtra. Sem {@code statusVenda}: Vendas
 * mostra as duas (concluídas e canceladas), Produtos e Serviços só as concluídas.
 */
public record FiltroRelatorioDTO(
        LocalDate dataInicio,
        LocalDate dataFim,
        Considerar considerar,
        List<Long> filialIds,
        Long vendedorId,
        Long clienteId,
        String q,
        StatusAvaliacaoProcedencia statusBko,
        StatusVendaFiltro statusVenda,
        List<SegmentoComercial> segmentos,
        List<TipoProduto> tiposProduto,
        Modo modo,
        AgruparPor agruparPor,
        List<String> campos
) {
}
