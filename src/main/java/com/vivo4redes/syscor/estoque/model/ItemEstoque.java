package com.vivo4redes.syscor.estoque.model;

import com.vivo4redes.syscor.estoque.enums.StatusSerial;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "tb_item_estoque", indexes = {
        @Index(name = "idx_serial_unico", columnList = "serial_imei", unique = true),
        @Index(name = "idx_prod_status", columnList = "produto_id, status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemEstoque {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "produto_id")
    private Produto produto;

    @Column(name = "serial_imei", nullable = false, unique = true, length = 50)
    private String serialImei;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatusSerial status;

    @Column(name = "deposito_sap", length = 10)
    private String depositoSap;

    @Column(name = "data_entrada")
    private LocalDateTime dataEntrada;

    @Column(name = "data_saida")
    private LocalDateTime dataSaida;

    @Version
    private Long version;

    @PrePersist
    public void prePersist() {
        this.dataEntrada = LocalDateTime.now();
        if (this.status == null) this.status = StatusSerial.DISPONIVEL;
        if (this.depositoSap == null) this.depositoSap = "DP01";
    }
}