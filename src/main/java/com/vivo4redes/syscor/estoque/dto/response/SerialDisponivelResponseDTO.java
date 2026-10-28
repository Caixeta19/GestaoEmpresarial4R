package com.vivo4redes.syscor.estoque.dto.response;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.util.EstoqueUtil;

import java.math.BigDecimal;

/**
 * Serial/IMEI disponível para o PDV, vindo de tb_estoque_geral.
 * Mantém os nomes de campo que o front (vendasApi.js) já consome.
 */
public record SerialDisponivelResponseDTO(
        Long itemId,
        String serialImei,
        Long produtoId,
        String sku,
        String descricao,
        String categoria,
        BigDecimal precoBase,
        BigDecimal valor,
        String cor,
        String filial
) {
    public static SerialDisponivelResponseDTO from(EstoqueGeral e) {
        BigDecimal preco = e.getValorVenda() != null ? e.getValorVenda() : BigDecimal.ZERO;
        String descricao = e.getModeloComercial() != null ? e.getModeloComercial() : e.getModeloDpgc();
        return new SerialDisponivelResponseDTO(
                e.getId(),
                e.getSerial(),
                e.getId(),                       // sem tb_produto: o id da linha faz o papel de produtoId
                e.getCodigoAcessorio(),
                descricao,
                EstoqueUtil.ehAcessorio(e) ? "ACESSORIO" : "PRODUTO_VIVO",
                preco,
                preco,
                e.getCor(),
                e.getFilialEstoque());
    }
}