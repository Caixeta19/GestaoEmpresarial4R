package com.vivo4redes.syscor.financeiro.relatorio.dto;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;

import java.time.LocalDate;
import java.util.List;

/** Filtros do relatório; listas vazias = sem restrição (dentro do escopo do usuário). */
public record FiltroFormasPagamentoDTO(LocalDate dataInicio, LocalDate dataFim, AgruparPor agruparPor,
                                       List<Long> filialIds, List<FormaPagamento> formas) {
}
