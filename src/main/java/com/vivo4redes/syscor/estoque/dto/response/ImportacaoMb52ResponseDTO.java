package com.vivo4redes.syscor.estoque.dto.response;

/**
 * Resumo da importação da MB52: linhas de dados lidas, da filial (centro/depósito), de outro centro, ignoradas
 * (total/subtotal/cabeçalho repetido) e SKUs com diferença depois do cálculo.
 */
public record ImportacaoMb52ResponseDTO(
        int linhas,
        int daFilial,
        int outroCentro,
        int ignoradas,
        long skusComDiferenca,
        ResumoMb52DTO mb52
) {
}
