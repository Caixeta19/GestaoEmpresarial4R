package com.vivo4redes.syscor.mailing.model;

import com.vivo4redes.syscor.mailing.enums.StatusCampanha;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_campanha_mailing")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CampanhaMailing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(name = "template_nome", nullable = false, length = 100)
    private String templateNome;

    @Column(name = "template_idioma", nullable = false, length = 10)
    @Builder.Default
    private String templateIdioma = "pt_BR";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private StatusCampanha status = StatusCampanha.RASCUNHO;

    @Column(name = "total_destinatarios", nullable = false)
    @Builder.Default
    private Integer totalDestinatarios = 0;

    @Column(name = "total_enviados", nullable = false)
    @Builder.Default
    private Integer totalEnviados = 0;

    @Column(name = "total_falhas", nullable = false)
    @Builder.Default
    private Integer totalFalhas = 0;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private Instant criadoEm = Instant.now();

    @OneToMany(mappedBy = "campanha", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<DestinatarioMailing> destinatarios = new ArrayList<>();
}