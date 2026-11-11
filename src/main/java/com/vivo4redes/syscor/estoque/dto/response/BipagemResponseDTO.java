package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;

import java.time.Instant;

/** Resposta imediata da leitura: o que o serial é, se já tinha sido bipado e os contadores atualizados. */
public record BipagemResponseDTO(
        Long itemId,
        String serial,
        ResultadoConciliacaoInventario resultado,
        String modelo,
        String mensagem,
        boolean jaBipado,
        Instant bipadoEm,
        String bipadoPor,
        TotaisInventarioDTO totais
) {
}
