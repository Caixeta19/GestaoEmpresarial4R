package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.financeiro.enums.TipoLancamentoCaixa;
import com.vivo4redes.syscor.venda.model.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Sangria, suprimento ou estorno de dinheiro num caixa aberto. Nunca é apagado nem alterado: errou, estorna. */
@Entity
@Table(name = "tb_caixa_lancamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaixaLancamento {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caixa_sessao_id", nullable = false)
    private CaixaSessao caixaSessao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private TipoLancamentoCaixa tipo;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Column(length = 200)
    private String descricao;

    /** Conta de destino (sangria) ou de origem (suprimento). */
    @Column(length = 100)
    private String conta;

    @Column(length = 300)
    private String observacao;

    /** Só no estorno. */
    @Column(length = 300)
    private String justificativa;

    /** Só no estorno: o lançamento que ele desfaz. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "estornado_de_id")
    private CaixaLancamento estornadoDe;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id")
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false)
    @Builder.Default
    private Instant criadoEm = Instant.now();

    @Column(name = "chave_idempotencia", nullable = false, length = 80)
    private String chaveIdempotencia;
}
