package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.model.PrecoItem;
import com.vivo4redes.syscor.estoque.model.ProdutoPrecoPlano;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoPrecoPlanoResponseDTO {
    private Long id;
    private String ofertaComunicacao;
    private BigDecimal preco;
    private String categoriaPlanilha;

    /** Formato antigo a partir da tabela nova: "oferta" = grupo do plano; categoria = aba da planilha. */
    public static ProdutoPrecoPlanoResponseDTO from(PrecoItem p) {
        return ProdutoPrecoPlanoResponseDTO.builder()
                .id(p.getId())
                .ofertaComunicacao(p.getGrupoPlano().name())
                .preco(p.getPreco())
                .categoriaPlanilha(p.getAba())
                .build();
    }

    public static ProdutoPrecoPlanoResponseDTO from(ProdutoPrecoPlano p) {
        return ProdutoPrecoPlanoResponseDTO.builder()
                .id(p.getId())
                .ofertaComunicacao(p.getOfertaComunicacao())
                .preco(p.getPreco())
                .categoriaPlanilha(p.getCategoriaPlanilha())
                .build();
    }
}