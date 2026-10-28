package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.util.EstoqueUtil;

import java.math.BigDecimal;

/** Item de catálogo (usado por /produtos), agora montado a partir de tb_estoque_geral. */
public record ProdutoResponseDTO(
        Long id,
        String sku,
        String descricao,
        String categoria,
        Integer estoqueMinimo,
        BigDecimal precoBase,
        Boolean requerSerial,
        Integer quantidade
) {
    public static ProdutoResponseDTO from(EstoqueGeral e) {
        boolean acessorio = EstoqueUtil.ehAcessorio(e);
        return new ProdutoResponseDTO(
                e.getId(),
                acessorio ? e.getCodigoAcessorio() : e.getSerial(),
                e.getModeloComercial() != null ? e.getModeloComercial() : e.getModeloDpgc(),
                acessorio ? "ACESSORIO" : "PRODUTO_VIVO",
                0,
                e.getValorVenda() != null ? e.getValorVenda() : BigDecimal.ZERO,
                !acessorio,
                e.getQuantidade());
    }
}