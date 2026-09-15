package com.vivo4redes.syscor.estoque.model;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

/**
 * Um preço resolvido de uma linha da tabela da Telefônica: produto (quando
 * encontrado no nosso catálogo) x oferta de comunicação (plano, pode ser
 * nulo para itens sem variação por plano) x preço da TABELA REGULAR.
 */
@Entity
@Table(name = "tb_produto_preco_plano", indexes = {
        @Index(name = "idx_produto_preco_plano_execucao2", columnList = "execucao_id"),
        @Index(name = "idx_produto_preco_plano_produto2", columnList = "produto_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProdutoPrecoPlano {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "execucao_id", nullable = false)
    private TabelaPrecoExecucao execucao;

    /** Nulo quando o Nome Comercial não resolveu para nenhum SKU do nosso catálogo. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Column(name = "sku_resolvido", length = 40)
    private String skuResolvido;

    @Column(name = "nome_comercial", length = 200)
    private String nomeComercial;

    @Column(name = "categoria_planilha", nullable = false, length = 60)
    private String categoriaPlanilha;

    @Column(name = "oferta_comunicacao", length = 120)
    private String ofertaComunicacao;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal preco;

    @Column(nullable = false)
    @Builder.Default
    private boolean encontrado = false;
}