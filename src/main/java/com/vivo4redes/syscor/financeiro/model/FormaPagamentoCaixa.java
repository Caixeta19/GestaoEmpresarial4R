package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Table(name = "tb_forma_pagamento_caixa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormaPagamentoCaixa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caixa_sessao_id", nullable = false)
    private CaixaSessao caixaSessao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private FormaPagamento forma;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal abertura = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal movimento = BigDecimal.ZERO;

    @Column(nullable = false, precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal saidas = BigDecimal.ZERO;

    @Column(name = "valor_conferido", precision = 12, scale = 2)
    private BigDecimal valorConferido;

    @Column(precision = 12, scale = 2)
    private BigDecimal diferenca;
}