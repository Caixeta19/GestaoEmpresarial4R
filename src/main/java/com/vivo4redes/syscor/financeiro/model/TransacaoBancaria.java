package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.financeiro.enums.ResultadoConciliacao;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "transacao_bancaria")
@Getter @Setter @NoArgsConstructor
public class TransacaoBancaria {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long webhookLogId;
    private String provider;
    private String idExterno;
    private String identificador;
    @Column(precision = 15, scale = 2)
    private BigDecimal valor;
    private OffsetDateTime liquidadoEm;
    @Enumerated(EnumType.STRING)
    private ResultadoConciliacao resultado;
    private Long tituloId;
    private String comprovanteUrl;
    private String motivo;
    private OffsetDateTime criadoEm = OffsetDateTime.now();
}