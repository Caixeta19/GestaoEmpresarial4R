package com.vivo4redes.syscor.estoque.model;
import com.vivo4redes.syscor.venda.enums.CategoriaItemVenda;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "tb_produto")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Produto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String sku;

    @Column(nullable = false, length = 150)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private CategoriaItemVenda categoria;

    @Column(name = "estoque_minimo", nullable = false)
    private Integer estoqueMinimo;

    @Column(name = "preco_base", nullable = false, precision = 12, scale = 2)
    private BigDecimal precoBase;

    @Column(name = "requer_serial", nullable = false)
    private Boolean requerSerial;

    @OneToMany(mappedBy = "produto", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ItemEstoque> itensEstoque = new ArrayList<>();

    @Column(name = "criado_em", updatable = false)
    private LocalDateTime criadoEm;

    @PrePersist
    public void prePersist() {
        this.criadoEm = LocalDateTime.now();
        if (this.estoqueMinimo == null) this.estoqueMinimo = 2;
        if (this.requerSerial == null) this.requerSerial = false;
    }
}