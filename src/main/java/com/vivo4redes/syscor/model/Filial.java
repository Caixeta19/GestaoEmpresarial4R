package com.vivo4redes.syscor.model;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Filial/loja onde a venda é registrada (ex: "CE - DEL PASEO").
 * Cadastro simples por enquanto — sem vínculo com RBAC/permissão por
 * filial, que fica para o Épico 0.
 */
@Entity
@Table(name = "filiais", uniqueConstraints = @UniqueConstraint(name = "uk_filial_codigo", columnNames = "codigo"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Filial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String codigo;

    @Column(nullable = false, length = 150)
    private String nome;

    @Column(nullable = false)
    @Builder.Default
    private boolean ativo = true;
}