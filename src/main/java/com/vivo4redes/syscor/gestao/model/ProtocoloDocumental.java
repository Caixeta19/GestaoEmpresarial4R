package com.vivo4redes.syscor.gestao.model;

import com.vivo4redes.syscor.gestao.enums.SituacaoServico;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.model.Venda;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "tb_protocolo_documental")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProtocoloDocumental {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venda_id", nullable = false, unique = true)
    private Venda venda;

    @Column(name = "numero_protocolo_ged", length = 50)
    private String numeroProtocoloGed;

    @Column(name = "data_digitalizacao")
    private LocalDate dataDigitalizacao;

    @Column(name = "zerar_remuneracao")
    private Boolean zerarRemuneracao;

    @Column(name = "gerar_price")
    private Boolean gerarPrice;

    @Column(name = "vencimento", length = 2)
    private String vencimento;

    @Column(name = "lider_equipe", length = 100)
    private String liderEquipe;

    @Column(columnDefinition = "TEXT")
    private String observacoes;

    @Column(name = "observacoes_importacao", columnDefinition = "TEXT")
    private String observacoesImportacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "situacao_servico", nullable = false, length = 20)
    @Builder.Default
    private SituacaoServico situacaoServico = SituacaoServico.CONFIRMADO;

    @Column(name = "gerar_comissao")
    @Builder.Default
    private Boolean gerarComissao = true;

    @Column(name = "motivos_cancelamento", columnDefinition = "TEXT")
    private String motivosCancelamento;

    @Column(name = "ativacao_primeira_chamada")
    private Boolean ativacaoPrimeiraChamada;

    @Column(name = "data_ativacao")
    private LocalDate dataAtivacao;

    @Column(name = "data_baixa")
    private LocalDate dataBaixa;

    @Column(name = "numero_protocolo_ged_portabilidade", length = 50)
    private String numeroProtocoloGedPortabilidade;

    @Column(name = "data_instalacao")
    private LocalDate dataInstalacao;

    /** Último usuário que salvou o protocolo (tela ou importação). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "alterado_por_id")
    private Usuario alteradoPor;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em")
    private Instant atualizadoEm;
}