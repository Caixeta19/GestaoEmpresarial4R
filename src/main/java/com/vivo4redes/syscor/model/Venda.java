package com.vivo4redes.syscor.model;
import com.vivo4redes.syscor.enums.StatusAvaliacaoProcedencia;
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

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "cliente_id", nullable = false)
    private Cliente cliente;

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
    private StatusVenda status = StatusVenda.PENDENTE;

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
}