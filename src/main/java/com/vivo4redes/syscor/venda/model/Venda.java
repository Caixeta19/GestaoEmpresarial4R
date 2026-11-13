package com.vivo4redes.syscor.venda.model;

import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import com.vivo4redes.syscor.venda.enums.StatusScoreCliente;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "vendas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Número público de 7 dígitos (ex: 1000000), gerado ao abrir a venda. Ver NumeroVendaGenerator. */
    @Column(name = "numero_venda", nullable = false, updatable = false, unique = true)
    private Long numeroVenda;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

    /** Filial/loja onde a venda foi registrada (tela "Início"). */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "filial_id", nullable = false)
    private Filial filial;

    /** Vendedor da venda: o usuário logado enquanto ABERTA (provisório); ao finalizar, quem se autenticou. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    /** Caixa em que a venda foi lançada (gravado ao finalizar); null nas vendas anteriores à V38 e nos carrinhos. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "caixa_sessao_id")
    private com.vivo4redes.syscor.financeiro.model.CaixaSessao caixaSessao;

    /** Quem se autenticou para finalizar a venda; null enquanto ABERTA e nas vendas antigas. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "finalizado_por_id")
    private Usuario finalizadoPor;

    @Column(name = "finalizado_em")
    private Instant finalizadoEm;

    /** Quem fez a última mudança por PATCH /vendas/{id}/status; procedência e expiração não têm autenticação. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "status_alterado_por_id")
    private Usuario statusAlteradoPor;

    @Column(name = "status_alterado_em")
    private Instant statusAlteradoEm;

    /** Dropdown "Venda Estoque Avançado?" da tela Início. */
    @Column(name = "estoque_avancado", nullable = false)
    @Builder.Default
    private boolean estoqueAvancado = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_score_cliente", nullable = false, length = 20)
    @Builder.Default
    private StatusScoreCliente statusScoreCliente = StatusScoreCliente.NAO_REALIZADA;

    @Column(name = "numero_serie_nota", length = 10)
    private String numeroSerieNota;

    @Column(name = "numero_nota", length = 20)
    private String numeroNota;

    @OneToMany(mappedBy = "venda", cascade = CascadeType.ALL, orphanRemoval = true)
    @BatchSize(size = 100) // listas do documental carregam os itens de várias vendas por consulta
    @Builder.Default
    private List<ItemVenda> itens = new ArrayList<>();

    /** Nunca setado diretamente pelo cliente da API — sempre recalculado a partir dos itens. */
    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal valorTotal = BigDecimal.ZERO;

    /** Renova ligado pelo número do voucher: aparelhos com preço por plano vão para a TABELA RENOVA. */
    @Column(name = "renova", nullable = false)
    @Builder.Default
    private boolean renova = false;

    /** Número do voucher do Renova. Não tem valor na venda: o preço Renova já traz o desconto do fabricante. */
    @Column(name = "codigo_voucher", length = 60)
    private String codigoVoucher;

    /**
     * Soma do desconto do fabricante dos aparelhos na RENOVA. Só controle/conciliação: o preço Renova já tem o
     * desconto, então ele não abate o total de novo. Sempre recalculado a partir dos itens.
     */
    @Column(name = "desconto_fabricante", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal descontoFabricante = BigDecimal.ZERO;

    /** Escolhida na aba Pagamento; define o preço dos aparelhos e acessórios com preço de tabela. */
    @Enumerated(EnumType.STRING)
    @Column(name = "condicao_pagamento", nullable = false, length = 20)
    @Builder.Default
    private CondicaoPagamento condicaoPagamento = CondicaoPagamento.A_VISTA;

    /** Soma no preço cheio, sem juros nem desconto PIX (na Renova, o preço cheio Renova). Sempre recalculado. */
    @Column(name = "valor_itens", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal valorItens = BigDecimal.ZERO;

    /** valorTotal − valorItens: negativo = desconto PIX, positivo = juros. Sempre recalculado. */
    @Column(name = "ajuste_condicao", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal ajusteCondicao = BigDecimal.ZERO;

    /** Financiado ÷ n em PARCELADO_n; valorTotal ÷ parcelas da condição nas demais. Sempre recalculado. */
    @Column(name = "valor_parcela", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal valorParcela = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusVenda status = StatusVenda.ABERTA;

    // length 30: "Em_avaliacao_pelo_BKO" tem 21 caracteres e estourava o limite de 20
    @Enumerated(EnumType.STRING)
    @Column(name = "avaliacao_procedencia", length = 30)
    private StatusAvaliacaoProcedencia avaliacaoProcedencia;

    /** Snapshot do consentimento do cliente no instante da venda — auditoria, não trava a venda. */
    @Column(name = "cliente_tinha_consentimento_marketing")
    private Boolean clienteTinhaConsentimentoMarketing;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public void adicionarItem(ItemVenda item) {
        item.setVenda(this);
        this.itens.add(item);
    }

    public void recalcularValorTotal() {
        this.valorTotal = itens.stream()
                .map(i -> i.getValorTotalItem().add(i.getValorChipCobrado()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
        this.descontoFabricante = itens.stream()
                .filter(i -> i.getTabelaAplicada() == TabelaPreco.RENOVA && i.getDescontoFabricante() != null)
                .map(i -> i.getDescontoFabricante().multiply(i.getQuantidade()))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    public void removerItem(Long itemId) {
        this.itens.removeIf(i -> i.getId().equals(itemId));
    }

    public long contarItensPorCategoria(CategoriaItemVenda categoria) {
        return itens.stream().filter(i -> i.getCategoria() == categoria).count();
    }
}