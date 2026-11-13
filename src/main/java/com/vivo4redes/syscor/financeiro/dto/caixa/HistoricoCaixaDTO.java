package com.vivo4redes.syscor.financeiro.dto.caixa;

import com.vivo4redes.syscor.financeiro.enums.AcaoCaixa;

import java.time.Instant;

public record HistoricoCaixaDTO(AcaoCaixa acao, String usuario, Instant em, String texto) {
}
