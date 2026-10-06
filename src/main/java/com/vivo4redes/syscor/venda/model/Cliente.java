package com.vivo4redes.syscor.venda.model;

import com.vivo4redes.syscor.venda.enums.TipoPessoa;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

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

    @Column(name = "cpf_cnpj", nullable = false, length = 20, updatable = false)
    private String cpfCnpj;

    @Column(name = "rg_ie", length = 30)
    private String rgIe;

    @Column(length = 2)
    private String uf;

    @Column(length = 100)
    private String cidade;

    @Column(length = 255)
    private String endereco;

    @Column(length = 100)
    private String complemento;

    @Column(length = 100)
    private String bairro;

    @Column(length = 10)
    private String cep;

    @Column(length = 150)
    private String email;

    @Column(length = 20)
    private String telefone;

    // LGPD: opt-in de marketing, com prova de qual termo foi aceito e quando
    @Column(name = "consentimento_marketing", nullable = false)
    @Builder.Default
    private boolean consentimentoMarketing = false;

    @Column(name = "versao_termo_consentimento", length = 50)
    private String versaoTermoConsentimento;

    @Column(name = "data_consentimento")
    private Instant dataConsentimento;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;

    @CreationTimestamp
    @Column(name = "criado_em", nullable = false, updatable = false)
    private Instant criadoEm;

    @UpdateTimestamp
    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;
}