package com.vivo4redes.syscor.venda.model;

import com.vivo4redes.syscor.estoque.model.ItemEstoque;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id", nullable = false)
    private Venda venda;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CategoriaItemVenda categoria;

    @Column(name = "produto_id", nullable = false)
    private Long produtoId;

    @Column(name = "descricao_produto", nullable = false, length = 200)
    private String descricaoProduto;

    @Column(nullable = false, precision = 12, scale = 3)
    private BigDecimal quantidade;

    @Column(name = "valor_unitario", nullable = false, precision = 12, scale = 2)
    private BigDecimal valorUnitario;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_estoque_id")
    private ItemEstoque itemEstoque;

    @Column(name = "serial_imei", length = 50)
    private String serialImei;

    @Column(name = "tabela_preco", length = 50)
    private String tabelaPreco;

    @Column(name = "sva")
    private Boolean sva;

    @Column(name = "seguro")
    private Boolean seguro;

    @Column(name = "segmento", length = 50)
    private String segmento;

    @Column(name = "tipo_servico", length = 50)
    private String tipoServico;

    @Column(name = "ddd", length = 2)
    private String ddd;

    @Column(name = "plano_antigo", length = 60)
    private String planoAntigo;

    @Column(name = "plano", length = 60)
    private String plano;

    @Column(name = "debito_automatico")
    private Boolean debitoAutomatico;

    @Column(name = "valor_adicional", precision = 12, scale = 2)
    private BigDecimal valorAdicional;

    @Column(name = "valor_acrescimo", precision = 12, scale = 2)
    private BigDecimal valorAcrescimo;

    @Column(name = "desconto", precision = 12, scale = 2)
    private BigDecimal desconto;

    @Column(name = "cupom")
    private Boolean cupom;

    @Column(name = "vencimento_fatura", length = 2)
    private String vencimentoFatura;

    @Column(name = "numero_acesso", length = 20)
    private String numeroAcesso;

    @Column(name = "sistema_origem", length = 10)
    private String sistemaOrigem;

    @Column(name = "num_ordem_next", length = 30)
    private String numOrdemNext;

    @Column(name = "num_solicitacao_ged", length = 30)
    private String numSolicitacaoGed;

    @Column(name = "simcard_3g", length = 30)
    private String simcard3g;

    @Column(name = "simcard_4g", length = 30)
    private String simcard4g;

    @Column(name = "cliente_possui_simcard")
    private Boolean clientePossuiSimcard;

    @Column(name = "simcard_doado")
    private Boolean simcardDoado;

    @Column(name = "desconto_chip", precision = 12, scale = 2)
    private BigDecimal descontoChip;

    @Column(name = "valor_chip", precision = 12, scale = 2)
    private BigDecimal valorChip;

    @Column(name = "serial_confirmado")
    private Boolean serialConfirmado;

    public BigDecimal getValorTotalItem() {
        return valorUnitario.multiply(quantidade).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}