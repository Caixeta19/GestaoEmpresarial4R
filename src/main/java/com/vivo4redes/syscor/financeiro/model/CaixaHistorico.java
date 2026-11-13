package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.financeiro.enums.AcaoCaixa;
import com.vivo4redes.syscor.venda.model.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Auditoria do caixa: quem abriu, fechou, conferiu ou reabriu, e quando. */
@Entity
@Table(name = "tb_caixa_historico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CaixaHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "caixa_sessao_id", nullable = false)
    private CaixaSessao caixaSessao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AcaoCaixa acao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    @Builder.Default
    private Instant em = Instant.now();

    @Column(length = 300)
    private String texto;
}
