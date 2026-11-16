package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.financeiro.enums.FormaPagamentoTitulo;
import com.vivo4redes.syscor.financeiro.enums.OrigemBaixa;
import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import com.vivo4redes.syscor.venda.model.Usuario;
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

    // ---- Contas a pagar (V41); nulos para RECEBER
    @Enumerated(EnumType.STRING)
    @Column(name = "forma_pagamento", length = 10)
    private FormaPagamentoTitulo formaPagamento;

    /** CPF ou CNPJ do fornecedor, só dígitos (11 ou 14). */
    @Column(name = "documento_fornecedor", length = 14)
    private String documentoFornecedor;

    /** De qual loja é a despesa (opcional). */
    @Column(name = "filial_id")
    private Long filialId;

    @Column(length = 60)
    private String categoria;

    @Column(length = 500)
    private String observacao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "criado_por_id")
    private Usuario criadoPor;

    @Enumerated(EnumType.STRING)
    @Column(name = "origem_baixa", length = 20)
    private OrigemBaixa origemBaixa;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "baixado_por_id")
    private Usuario baixadoPor;

    /** Justificativa da baixa manual. */
    @Column(name = "justificativa_baixa", length = 300)
    private String justificativaBaixa;

    @Column(name = "cancelado_em")
    private OffsetDateTime canceladoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cancelado_por_id")
    private Usuario canceladoPor;

    @Column(name = "motivo_cancelamento", length = 300)
    private String motivoCancelamento;

    /** Lançamento do extrato que baixou a conta (preenchido na conciliação pelo extrato). */
    @Column(name = "extrato_lancamento_id")
    private Long extratoLancamentoId;

    /** Lock otimista. */
    @Version
    private Long versao;

    /** Pode ser baixado (webhook, extrato ou baixa manual): só ABERTO e DIVERGENTE. */
    public boolean podeSerBaixado() {
        return this.status != null && this.status.editavel();
    }

    public boolean editavel() {
        return this.status != null && this.status.editavel();
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
        this.origemBaixa = OrigemBaixa.CONCILIACAO_BB;
    }

    /** Baixa feita por pessoa (pagou por outro meio, ou confirmou o vínculo com o extrato). */
    public void baixarManual(BigDecimal valorPago, OffsetDateTime quando, String banco, Usuario quem,
                             String justificativa) {
        this.status = StatusTitulo.BAIXADO;
        this.valorPago = valorPago;
        this.pagoEm = quando;
        this.dataBaixa = quando.toLocalDate();
        this.bancoPagador = banco;
        this.origemBaixa = OrigemBaixa.MANUAL;
        this.baixadoPor = quem;
        this.justificativaBaixa = justificativa;
    }

    public void cancelar(Usuario quem, String motivo) {
        this.status = StatusTitulo.CANCELADO;
        this.canceladoEm = OffsetDateTime.now();
        this.canceladoPor = quem;
        this.motivoCancelamento = motivo;
    }

    /** Desfaz uma baixa errada: limpa tudo o que a baixa gravou, inclusive o vínculo com o extrato. */
    public void reabrir() {
        this.status = StatusTitulo.ABERTO;
        this.valorPago = null;
        this.pagoEm = null;
        this.dataBaixa = null;
        this.bancoPagador = null;
        this.origemBaixa = null;
        this.baixadoPor = null;
        this.justificativaBaixa = null;
        this.extratoLancamentoId = null;
    }
}
