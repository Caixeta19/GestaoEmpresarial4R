package com.vivo4redes.syscor.estoque.service;

import java.time.LocalDate;

/** Uma linha da IQ09 (export do SAP), já com os textos limpos. {@code numeroLinha} é a linha no arquivo (1 = cabeçalho). */
record LinhaSapDTO(
        int numeroLinha,
        String material,
        String denominacao,
        String serial,
        String centro,
        String deposito,
        String tipoEstoque,
        String statusSistema,
        String statusUsuario,
        String categoriaEquipamento,
        LocalDate modificadoEm,
        String modificadoPor
) {
}
