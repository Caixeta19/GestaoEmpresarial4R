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

    // ---------------------------------------------------------------
    // Campos específicos de Produto Vivo / Serviço Vivo (telecom).
    // Todos opcionais — a aplicabilidade depende da categoria do item;
    // ver Venda.jsx (formProdutoVivo / servicoForm) no frontend.
    // ---------------------------------------------------------------

    @Column(name = "tabela_preco", length = 50)
    private String tabelaPreco;

    @Column(name = "sva")
    private Boolean sva;

    @Column(name = "seguro")
    private Boolean seguro;

    @Column(name = "segmento", length = 50)
    private String segmento;

    /** Tipo de operação: Troca de Aparelho, Alta, Migração, Reativação, Troca de Simcard, Troca de titularidade... */
    @Column(name = "tipo_servico", length = 50)
    private String tipoServico;

    @Column(name = "ddd", length = 2)
    private String ddd;

    /** Só usado em Troca de Plano — o plano que o cliente tinha antes da operação. */
    @Column(name = "plano_antigo", length = 60)
    private String planoAntigo;

    /** Plano ativo/contratado após a operação (formProdutoVivo.planoAtivo / servicoForm.planoNovo). */
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

    /** Dia do vencimento da fatura, ex.: "10". */
    @Column(name = "vencimento_fatura", length = 2)
    private String vencimentoFatura;

    /** MSISDN — número da linha ativada/portada/alterada neste item. */
    @Column(name = "numero_acesso", length = 20)
    private String numeroAcesso;

    /** Sistema de origem do lançamento: VIVO+, NEXT ou GED. */
    @Column(name = "sistema_origem", length = 10)
    private String sistemaOrigem;

    @Column(name = "num_ordem_next", length = 30)
    private String numOrdemNext;

    @Column(name = "num_solicitacao_ged", length = 30)
    private String numSolicitacaoGed;

    /** ICCID do chip 3G, quando aplicável (troca de simcard). */
    @Column(name = "simcard_3g", length = 30)
    private String simcard3g;

    /** ICCID do chip 4G, quando aplicável (troca de simcard). */
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