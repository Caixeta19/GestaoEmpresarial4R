package com.vivo4redes.syscor.model;
import com.vivo4redes.syscor.enums.StatusAvaliacaoProcedencia;
import com.vivo4redes.syscor.enums.StatusScoreCliente;
import com.vivo4redes.syscor.enums.StatusVenda;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
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

    /** Vendedor autenticado que abriu/está operando a venda (tela "Início"). */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

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
    @Builder.Default
    private List<ItemVenda> itens = new ArrayList<>();

    /** Nunca setado diretamente pelo cliente da API — sempre recalculado a partir dos itens. */
    @Column(name = "valor_total", nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal valorTotal = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusVenda status = StatusVenda.ABERTA;

    @Enumerated(EnumType.STRING)
    @Column(name = "avaliacao_procedencia", length = 20)
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
                .map(ItemVenda::getValorTotalItem)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public void removerItem(Long itemId) {
        this.itens.removeIf(i -> i.getId().equals(itemId));
    }

    public long contarItensPorCategoria(com.vivo4redes.syscor.enums.CategoriaItemVenda categoria) {
        return itens.stream().filter(i -> i.getCategoria() == categoria).count();
    }
}