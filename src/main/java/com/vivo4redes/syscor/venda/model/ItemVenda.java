package com.vivo4redes.syscor.venda.model;

import com.vivo4redes.syscor.estoque.model.ItemEstoque;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Item de venda. Integra opcionalmente com o módulo de Estoque via ItemEstoque
 * quando o item exige baixa de um serial/IMEI (US-203).
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

    /** Categoria da aba de origem na UI (Produto Vivo / Serviço Vivo / Recarga). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaItemVenda categoria;

    /** Referência lógica ao futuro Produto/Serviço do módulo de Estoque (sem FK física ainda). */
    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Column(name = "descricao_produto", nullable = false, length = 150)
    private String descricaoProduto;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    /** Serial/IMEI baixado no Estoque para este item, quando aplicável. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_estoque_id")
    private ItemEstoque itemEstoque;

    /** Snapshot do serial/IMEI informado ou baixado, para exibição mesmo se o vínculo com estoque mudar. */
    @Column(name = "serial_imei", length = 50)
    private String serialImei;

    public BigDecimal getValorTotalItem() {
        return valorUnitario.multiply(quantidade);
    }
}