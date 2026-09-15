package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.model.ProdutoPrecoPlano;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Alimenta a tela de tabs por oferta (Pré, Vivo Controle 15GB, Vivo Total Família 3...). */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoComPrecoResponseDTO {
    private Long produtoId;
    private String sku;
    private String descricao;
    private String ofertaComunicacao;
    private BigDecimal preco;

    public static ProdutoComPrecoResponseDTO from(ProdutoPrecoPlano p) {
        return ProdutoComPrecoResponseDTO.builder()
                .produtoId(p.getProduto().getId())
                .sku(p.getProduto().getSku())
                .descricao(p.getProduto().getDescricao())
                .ofertaComunicacao(p.getOfertaComunicacao())
                .preco(p.getPreco())
                .build();
    }
}