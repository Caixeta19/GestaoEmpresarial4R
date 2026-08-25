package com.vivo4redes.syscor.model;

import com.vivo4redes.syscor.enums.TipoPessoa;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * Cliente do sistema.
 *
 * <p>Cadastro com consentimento LGPD embutido desde a fundação — pré-requisito
 * legal para qualquer venda (ver documentação SYSCOR, seção 4). As regras mais
 * profundas de negócio ligadas a vendas (histórico, exclusividade de linha
 * etc.) ficam para o módulo de Vendas; aqui é só o cadastro base.
 */
@Entity
@Table(name = "cliente")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "id")
@ToString
public class Cliente {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_pessoa", nullable = false, length = 10)
    private TipoPessoa tipoPessoa;

    @Column(name = "nome", nullable = false, length = 150)
    private String nome;

    @Column(name = "cpf_cnpj", nullable = false, length = 20)
    private String cpfCnpj;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "telefone", length = 20)
    private String telefone;

    @Column(name = "consentimento_lgpd", nullable = false)
    @Builder.Default
    private boolean consentimentoLgpd = false;

    @Column(name = "data_consentimento")
    private LocalDateTime dataConsentimento;

    @Column(name = "data_revogacao_consentimento")
    private LocalDateTime dataRevogacaoConsentimento;

    @Column(name = "ativo", nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private LocalDateTime criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    /**
     * Registra o opt-in de consentimento LGPD, com timestamp.
     * Regra central: nenhuma venda deve seguir em frente sem isso = true.
     */
    public void concederConsentimento() {
        this.consentimentoLgpd = true;
        this.dataConsentimento = LocalDateTime.now();
        this.dataRevogacaoConsentimento = null;
    }

    /**
     * Registra o opt-out (revogação) de consentimento LGPD, com timestamp.
     */
    public void revogarConsentimento() {
        this.consentimentoLgpd = false;
        this.dataRevogacaoConsentimento = LocalDateTime.now();
    }
}