package com.vivo4redes.syscor.financeiro.dto.response;

import com.vivo4redes.syscor.financeiro.enums.AcaoTitulo;

import java.time.Instant;

public record HistoricoTituloDTO(AcaoTitulo acao, String usuario, Instant em, String texto) {
}
