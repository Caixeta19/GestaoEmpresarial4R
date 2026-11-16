package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.financeiro.enums.AcaoTitulo;
import com.vivo4redes.syscor.venda.model.Usuario;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/** Auditoria da conta a pagar: quem criou, editou, cancelou, baixou ou reabriu, e quando. Nunca se apaga linha. */
@Entity
@Table(name = "tb_titulo_historico")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TituloHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "titulo_id", nullable = false)
    private TituloFinanceiro titulo;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AcaoTitulo acao;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    private Usuario usuario;

    @Column(nullable = false)
    @Builder.Default
    private Instant em = Instant.now();

    @Column(length = 500)
    private String texto;
}
