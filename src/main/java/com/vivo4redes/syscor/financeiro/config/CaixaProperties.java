package com.vivo4redes.syscor.financeiro.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Quem pode o quê no caixa (prefixo {@code syscor.caixa}). Os cargos são comparados pelo mesmo papel que o login gera
 * ({@code ROLE_<CARGO>}, sem acento): "GERENTE DE VENDAS - JÚNIOR" casa com "gerente de vendas - junior".
 */
@Data
@Component
@ConfigurationProperties(prefix = "syscor.caixa")
public class CaixaProperties {

    /** Abrem e fecham o caixa só da própria filial. */
    private List<String> cargosGerente = new ArrayList<>();

    /** Abrem e fecham em qualquer filial, e também conferem e reabrem. */
    private List<String> cargosAuditoria = new ArrayList<>();

    /** Conferem e reabrem; não abrem nem fecham. */
    private List<String> cargosFinanceiro = new ArrayList<>();

    /** Logins com todos os poderes sobre o caixa. */
    private List<String> admins = new ArrayList<>();

    /** Caixa de dia anterior ainda aberto bloqueia a venda de hoje (por padrão só avisa). */
    private boolean bloquearComCaixaAnteriorAberto = false;
}
