package com.vivo4redes.syscor.financeiro;

import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Sem caixa aberto hoje a filial não vende (docs/financeiro_caixa.md): os testes que lançam venda abrem um caixa de
 * hoje para a filial por aqui, direto no banco (sem passar por perfil), com as seis formas do caixa em 0.
 */
public final class CaixaTestSupport {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final String[] FORMAS = {"DINHEIRO", "PIX", "VIVO_PAY", "CARTAO_CREDITO", "CARTAO_DEBITO", "BOLETO"};

    private CaixaTestSupport() {
    }

    /** Abre o "CAIXA TESTE" de hoje da filial e devolve o id da sessão. */
    public static long abrirCaixaDeHoje(JdbcTemplate jdbc, long filialId) {
        return abrirCaixa(jdbc, filialId, "CAIXA TESTE", LocalDate.now(ZONA));
    }

    public static long abrirCaixa(JdbcTemplate jdbc, long filialId, String caixaPdv, LocalDate data) {
        Long id = jdbc.queryForObject("""
                insert into tb_caixa_sessao (filial_id, caixa_pdv, data_abertura, status, aberto_em, conferencia)
                values (?, ?, ?, 'ABERTO', now(), 'AGUARDANDO') returning id
                """, Long.class, filialId, caixaPdv, data);
        for (String forma : FORMAS) {
            jdbc.update("insert into tb_forma_pagamento_caixa (caixa_sessao_id, forma) values (?, ?)", id, forma);
        }
        return id;
    }
}
