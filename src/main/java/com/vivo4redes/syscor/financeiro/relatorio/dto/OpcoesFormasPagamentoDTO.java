package com.vivo4redes.syscor.financeiro.relatorio.dto;

import java.util.List;

/** Opções dos filtros do relatório, já no escopo do usuário. */
public record OpcoesFormasPagamentoDTO(List<FilialOpcao> filiais, List<FormaOpcao> formas) {

    public record FilialOpcao(Long id, String codigo, String nome) {
    }

    public record FormaOpcao(String valor, String rotulo) {
    }
}
