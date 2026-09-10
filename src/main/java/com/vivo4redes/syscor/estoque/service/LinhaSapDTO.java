package com.vivo4redes.syscor.estoque.service;


record LinhaSapDTO(
        String material,
        String denominacao,
        String serial,
        String centro,
        String deposito,
        String tipoEstoque,
        String statusSistema,
        String modificadoEm,
        String modificadoPor
) {
}