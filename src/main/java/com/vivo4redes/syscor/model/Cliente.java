package com.vivo4redes.syscor.model;
import com.vivo4redes.syscor.enums.TipoPessoa;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "clientes", uniqueConstraints = @UniqueConstraint(name = "uk_cliente_cpf_cnpj", columnNames = "cpf_cnpj"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Cliente {

 @Id
 @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

 @Enumerated(EnumType.STRING)
 @Column(name = "tipo_pessoa", nullable = false, length = 20)
    private TipoPessoa tipoPessoa;

 @Column(nullable = false, length = 150)
    private String nome;

 @Column(name = "cpf_cnpj", nullable = false, length = 14,updatable = false)
    private String cpfCnpj;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String telefone;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    // --- LGPD / consentimento (US-301) ---
    @Column(name = "consentimento_marketing", nullable = false)
    @Builder.Default
    private boolean consentimentoMarketing = false;

    @Column(name = "consentimento_data_hora")
    private LocalDateTime consentimentoDataHora;

    @Column(name = "consentimento_versao_termo", length = 20)
    private String consentimentoVersaoTermo;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}

