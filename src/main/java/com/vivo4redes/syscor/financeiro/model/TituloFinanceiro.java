package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** US-101/102/104: contas a pagar e a receber, com baixa manual. */
@Entity
@Table(name = "tb_titulo_financeiro")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TituloFinanceiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TipoTitulo tipo;

    @Column(nullable = false, length = 200)
    private String descricao;

    /** Cliente (a receber) ou fornecedor (a pagar) — texto livre, não é FK. */
    @Column(nullable = false, length = 150)
    private String quem;

    @Column(nullable = false)
    private LocalDate vencimento;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private StatusTitulo status = StatusTitulo.ABERTO;

    @Column(name = "data_baixa")
    private LocalDate dataBaixa;

    /** Vínculo opcional com a venda de origem (ex.: título "a receber" gerado a partir de uma venda). */
    @Column(name = "venda_id")
    private Long vendaId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private Instant criadoEm = Instant.now();
}