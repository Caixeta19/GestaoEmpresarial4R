package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.financeiro.enums.OrigemConciliacao;
import com.vivo4redes.syscor.financeiro.enums.SituacaoExtrato;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/** Débito contabilizado do extrato do banco. Nunca se apaga linha; só débitos entram (créditos não interessam). */
@Entity
@Table(name = "tb_extrato_lancamento")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtratoLancamento {

    public static final String BANCO_BRASIL = "BANCO_BRASIL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30)
    @Builder.Default
    private String provider = BANCO_BRASIL;

    /** {@code textoIdentificadorUnicoTransacao} da v2 ou, sem ele, "H:" + SHA-256 de campos estáveis do lançamento. */
    @Column(name = "id_externo", nullable = false, length = 80)
    private String idExterno;

    @Column(name = "data_lancamento", nullable = false)
    private LocalDate dataLancamento;

    /** Sempre positivo (só o sinal D é gravado). */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "numero_documento")
    private Long numeroDocumento;

    @Column(name = "numero_lote")
    private Integer numeroLote;

    @Column(name = "codigo_historico")
    private Integer codigoHistorico;

    @Column(name = "sub_historico", length = 25)
    private String subHistorico;

    @Column(name = "info_complementar", length = 38)
    private String infoComplementar;

    /** CPF/CNPJ da contrapartida, só dígitos; pode vir vazio em pagamento de boleto. */
    @Column(name = "contraparte_documento", length = 15)
    private String contraparteDocumento;

    @Column(name = "contraparte_tipo", length = 1)
    private String contraparteTipo;

    @Column(name = "contraparte_banco")
    private Integer contraparteBanco;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private SituacaoExtrato situacao = SituacaoExtrato.NOVO;

    @Column(name = "titulo_id")
    private Long tituloId;

    @Column(name = "conciliado_em")
    private OffsetDateTime conciliadoEm;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private OrigemConciliacao origem;

    @Column(length = 300)
    private String motivo;

    /** Baixa desfeita ao reabrir a conta: o reprocesso automático não casa de novo. */
    @Column(nullable = false)
    @Builder.Default
    private boolean desvinculado = false;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_raw", columnDefinition = "jsonb")
    private String payloadRaw;

    @Column(name = "criado_em", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime criadoEm = OffsetDateTime.now();

    /** Texto do banco para a tela e o comprovante: subhistórico + informação complementar. */
    public String historicoTexto() {
        String sub = subHistorico == null ? "" : subHistorico.trim();
        String info = infoComplementar == null ? "" : infoComplementar.trim();
        return (sub + " " + info).trim();
    }
}
