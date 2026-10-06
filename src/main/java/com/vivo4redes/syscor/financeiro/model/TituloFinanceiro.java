package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;

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

    /** Vínculo opcional com a venda de origem. */
    @Column(name = "venda_id")
    private Long vendaId;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private Instant criadoEm = Instant.now();

    /** ID de correlação enviado ao banco ao agendar o pagamento. */
    @Column(name = "identificador_pagamento", length = 100)
    private String identificadorPagamento;

    /** Fallback de matching. */
    @Column(name = "codigo_barras", length = 60)
    private String codigoBarras;

    @Column(name = "banco_pagador", length = 30)
    private String bancoPagador;

    @Column(name = "pago_em")
    private OffsetDateTime pagoEm;

    @Column(name = "valor_pago", precision = 15, scale = 2)
    private BigDecimal valorPago;

    @Column(name = "transacao_bancaria_id")
    private Long transacaoBancariaId;

    @Column(name = "comprovante_url")
    private String comprovanteUrl;

    /** Lock otimista. */
    @Version
    private Long versao;

    public boolean podeSerBaixado() {
        return this.status != StatusTitulo.BAIXADO;
    }

    public void marcarDivergente() {
        this.status = StatusTitulo.DIVERGENTE;
    }

    public void baixarPorConciliacao(BigDecimal valorPago, OffsetDateTime quando, String banco,
                                     Long transacaoId, String comprovante) {
        this.status = StatusTitulo.BAIXADO;
        this.valorPago = valorPago;
        this.pagoEm = quando;
        this.dataBaixa = quando != null ? quando.toLocalDate() : LocalDate.now();
        this.bancoPagador = banco;
        this.transacaoBancariaId = transacaoId;
        this.comprovanteUrl = comprovante;
    }
}