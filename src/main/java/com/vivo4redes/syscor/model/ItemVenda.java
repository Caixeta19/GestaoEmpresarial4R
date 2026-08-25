package com.vivo4redes.syscor.model;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;

/**
 * Item de venda. O módulo de Estoque ainda não existe, então guardamos
 * apenas a referência (produtoId) e um snapshot de descrição/preço — quando
 * o módulo de Estoque nascer, plugamos a baixa automática (US-203) via um
 * EstoquePort, sem alterar esta entidade.
 */
@Entity
@Table(name = "itens_venda")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemVenda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    /** Referência lógica ao futuro Produto do módulo de Estoque (sem FK física ainda). */
    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Column(name = "descricao_produto", nullable = false, length = 150)
    private String descricaoProduto;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    public BigDecimal getValorTotalItem() {
        return valorUnitario.multiply(quantidade);
    }
}