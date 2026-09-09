package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.model.Produto;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoResponseDTO {
    private Long id;
    private String sku;
    private String descricao;
    private CategoriaItemVenda categoria;
    private Integer estoqueMinimo;
    private BigDecimal precoBase;
    private Boolean requerSerial;

    public static ProdutoResponseDTO from(Produto p) {
        return ProdutoResponseDTO.builder()
                .id(p.getId())
                .sku(p.getSku())
                .descricao(p.getDescricao())
                .categoria(p.getCategoria())
                .estoqueMinimo(p.getEstoqueMinimo())
                .precoBase(p.getPrecoBase())
                .requerSerial(p.getRequerSerial())
                .build();
    }
}