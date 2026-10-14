package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.model.ItemEstoque;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import java.math.BigDecimal;

/** Um IMEI/serial disponível para venda, já com os dados do produto (para o PDV). */
public record SerialDisponivelResponseDTO(
        Long itemId,
        String serialImei,
        Long produtoId,
        String sku,
        String descricao,
        CategoriaItemVenda categoria,
        BigDecimal precoBase
) {
    public static SerialDisponivelResponseDTO from(ItemEstoque i) {
        var p = i.getProduto();
        return new SerialDisponivelResponseDTO(
                i.getId(), i.getSerialImei(), p.getId(), p.getSku(), p.getDescricao(), p.getCategoria(), p.getPrecoBase());
    }
}