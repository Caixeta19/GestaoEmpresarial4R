package com.vivo4redes.syscor.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Vendedor do módulo de Vendas. Entidade própria e mínima criada porque a
 * tela exige reautenticação por senha ao salvar/alterar uma venda, mesmo
 * antes do Épico 0 (JWT/RBAC) existir. Quando a autenticação global chegar,
 * este cadastro deve ser unificado com o "usuário do sistema" do Épico 0 —
 * hoje ele guarda apenas o necessário para a confirmação de identidade
 * (email + hash de senha), sem papéis/permissões.
 */
@Entity
@Table(name = "vendedores", uniqueConstraints = @UniqueConstraint(name = "uk_vendedor_email", columnNames = "email"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Vendedor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false, length = 150)
    private String email;

    /** Sempre um hash BCrypt — nunca texto plano. */
    @Column(name = "senha_hash", nullable = false, length = 100)
    private String senhaHash;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;
}