package com.vivo4redes.syscor.financeiro.model;

import com.vivo4redes.syscor.financeiro.enums.OrigemSincronizacao;
import com.vivo4redes.syscor.financeiro.enums.StatusSincronizacao;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

/** Uma rodada de sincronização com o extrato (agendada ou "Sincronizar agora"). */
@Entity
@Table(name = "tb_extrato_sincronizacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ExtratoSincronizacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private OffsetDateTime inicio;

    @Column(nullable = false)
    private OffsetDateTime fim;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private StatusSincronizacao status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private OrigemSincronizacao origem;

    @Column(name = "disparado_por_id")
    private Long disparadoPorId;

    @Column(name = "lancamentos_novos", nullable = false)
    private int lancamentosNovos;

    @Column(nullable = false)
    private int conciliados;

    @Column(length = 500)
    private String erro;
}
