package com.vivo4redes.syscor.gestao.dto.response;

import java.util.List;

/** Resumo da importação em lote: cada linha é gravada sozinha, então erros não desfazem as outras. */
public record ResultadoImportacaoProtocolosDTO(int total, int sucessos, List<Erro> erros) {

    /** {@code linha} conta o cabeçalho como linha 1. */
    public record Erro(int linha, String numeroVenda, String motivo) {
    }
}
