package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.model.PrecoItem;
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

    /**
     * Formato antigo a partir da tabela nova: "oferta" = grupo do plano. Sem unidade "representante":
     * {@code produtoId} vem nulo e {@code sku} é a chave do Nome Comercial.
     */
    public static ProdutoComPrecoResponseDTO from(PrecoItem p) {
        return ProdutoComPrecoResponseDTO.builder()
                .produtoId(null)
                .sku(p.getChaveNome())
                .descricao(p.getNomeComercial())
                .ofertaComunicacao(p.getGrupoPlano().name())
                .preco(p.getPreco())
                .build();
    }

    public static ProdutoComPrecoResponseDTO from(ProdutoPrecoPlano p) {
        return ProdutoComPrecoResponseDTO.builder()
                .produtoId(p.getProdutoId())
                .sku(p.getSkuResolvido())
                .descricao(p.getNomeComercial())
                .ofertaComunicacao(p.getOfertaComunicacao())
                .preco(p.getPreco())
                .build();
    }
}