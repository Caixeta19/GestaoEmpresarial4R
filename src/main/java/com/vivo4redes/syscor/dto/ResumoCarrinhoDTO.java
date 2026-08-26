package com.vivo4redes.syscor.dto;

import java.math.BigDecimal;

/**
 * Contador de itens por categoria, para renderizar os badges das abas da UI
 * (ex: "Produto Vivo (3)", "Serviço Vivo (0)", "Recarga (0)") sem precisar
 * trafegar a lista completa de itens a cada atualização do carrinho.
 */
public record ResumoCarrinhoDTO(
        Long vendaId,
        long qtdProdutoVivo,
        long qtdServicoVivo,
        long qtdRecarga,
        BigDecimal valorTotal
) {
}


