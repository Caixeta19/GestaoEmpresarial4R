package com.vivo4redes.syscor.venda.dto;

import java.time.LocalDate;

public record VendaFiltroDTO(
        Long filialId,
        Long vendedorId,
        Long numeroVenda,
        LocalDate dataInicio,
        LocalDate dataFim,
        String cliente,
        String numeroAcesso,
        String serialProdutoVivo,
        String serialSimcard,
        String modeloAcessorio
) {
}