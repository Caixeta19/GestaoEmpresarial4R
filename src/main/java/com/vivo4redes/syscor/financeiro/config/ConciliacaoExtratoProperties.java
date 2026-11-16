package com.vivo4redes.syscor.financeiro.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** Regras da conciliação das contas a pagar com o extrato (prefixo {@code syscor.financeiro.conciliacao}). */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "syscor.financeiro.conciliacao")
public class ConciliacaoExtratoProperties {

    /**
     * true (padrão): conta única sem nenhum reforço vira AMBIGUO (a pessoa confirma com 1 clique). false: baixa
     * sozinha por valor + vencimento na janela. Mais seguro errar para "pedir confirmação".
     */
    private boolean exigirReforco = true;

    /**
     * {@code codigoHistorico} do BB que indicam pagamento (boleto, convênio, Pix, TED). Vazio até a calibragem com
     * extrato real de homologação; sem lista, o reforço fraco nunca dispara.
     */
    private List<Integer> codigosHistoricoPagamento = new ArrayList<>();

    /** Reforço fraco: lançamento a até N dias do vencimento. */
    private int proximidadeVencimentoDias = 7;
}
