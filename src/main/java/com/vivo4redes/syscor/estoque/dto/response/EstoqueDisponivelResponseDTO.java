package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.model.EstoqueGeral;

import java.math.BigDecimal;

/**
 * Unidade disponível carregada na tela de venda (PDV). O {@code id} é o que o
 * carrinho envia como {@code produtoId} — a unidade É o produto.
 */
public record EstoqueDisponivelResponseDTO(
        Long id,
        String serial,
        String tipoProduto,
        String categoria,
        String marca,
        String modeloComercial,
        String cor,
        String codigoAcessorio,
        String filialEstoque,
        BigDecimal valorVenda,
        /** Unidades livres da linha (acessório tem um serial por SKU com quantidade N). */
        Integer quantidadeDisponivel,
        /** Vai na aba Acessório (código de acessório, ou categoria de acessório sem IMEI). */
        boolean acessorio
) {
    public static EstoqueDisponivelResponseDTO from(EstoqueGeral e) {
        return new EstoqueDisponivelResponseDTO(
                e.getId(), e.getSerial(), e.getTipoProduto(), e.getCategoria(), e.getMarca(),
                e.getModeloComercial(), e.getCor(), e.getCodigoAcessorio(), e.getFilialEstoque(),
                e.getValorVenda(), e.quantidadeLivre(),
                com.vivo4redes.syscor.util.EstoqueUtil.vendidoComoAcessorio(e));
    }
}