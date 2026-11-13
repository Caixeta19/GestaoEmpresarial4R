package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.financeiro.enums.ConferenciaCaixa;
import com.vivo4redes.syscor.financeiro.enums.StatusCaixaSessao;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.model.Filial;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Tela "Conferência de Caixa": uma sessão = um caixa aberto num PDV num dia.
 * "entradas" (soma dos pagamentos das vendas CONCLUIDAS lançadas neste caixa, via vendas.caixa_sessao_id) nunca é
 * armazenado aqui — é sempre calculado ao vivo (ver CaixaSessaoService),
 * pra nunca ficar desatualizado em relação às vendas reais.
 */
@Entity
@Table(name = "tb_caixa_sessao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaixaSessao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "filial_id", nullable = false)
    private Filial filial;

    /** Nome do caixa dentro do PDV, ex.: "CAIXA CANINDE" — um PDV pode ter mais de um caixa físico. */
    @Column(name = "caixa_pdv", nullable = false, length = 100)
    private String caixaPdv;

    @Column(name = "data_abertura", nullable = false)
    private LocalDate dataAbertura;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    @Builder.Default
    private StatusCaixaSessao status = StatusCaixaSessao.ABERTO;

    @Column(name = "aberto_em", nullable = false)
    @Builder.Default
    private Instant abertoEm = Instant.now();

    @Column(name = "fechado_em")
    private Instant fechadoEm;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aberto_por_id")
    private Usuario abertoPor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fechado_por_id")
    private Usuario fechadoPor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    @Builder.Default
    private ConferenciaCaixa conferencia = ConferenciaCaixa.AGUARDANDO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conferido_por_id")
    private Usuario conferidoPor;

    @Column(name = "conferido_em")
    private Instant conferidoEm;

    /** Obrigatória ao fechar com diferença. */
    @Column(length = 300)
    private String observacao;

    /** Obrigatória quando o gerente muda o valor sugerido de abertura do dinheiro. */
    @Column(name = "justificativa_abertura", length = 300)
    private String justificativaAbertura;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reaberto_por_id")
    private Usuario reabertoPor;

    @Column(name = "reaberto_em")
    private Instant reabertoEm;

    @Column(name = "justificativa_reabertura", length = 300)
    private String justificativaReabertura;

    @OneToMany(mappedBy = "caixaSessao", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<FormaPagamentoCaixa> formasPagamento = new ArrayList<>();
}