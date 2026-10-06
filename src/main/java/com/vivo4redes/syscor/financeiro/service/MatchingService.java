package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.financeiro.dto.response.ConciliacaoEventoDTO;
import com.vivo4redes.syscor.financeiro.enums.ResultadoConciliacao;
import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;
import com.vivo4redes.syscor.financeiro.model.TransacaoBancaria;
import com.vivo4redes.syscor.financeiro.model.WebhookLog;
import com.vivo4redes.syscor.financeiro.repository.TituloFinanceiroRepository;
import com.vivo4redes.syscor.financeiro.repository.TransacaoBancariaRepository;
import com.vivo4redes.syscor.financeiro.webhook.EventoPagamento;
import java.time.OffsetDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Cruza o evento do banco com o título a pagar. Roda dentro da transação do ConciliacaoProcessor. */
@Service
@RequiredArgsConstructor
public class MatchingService {

    private final TituloFinanceiroRepository titulos;
    private final TransacaoBancariaRepository transacoes;

    public ConciliacaoEventoDTO conciliar(EventoPagamento ev, WebhookLog log) {

        // 1) idempotência por transação: a mesma transação do banco nunca é aplicada duas vezes
        if (transacoes.existsByProviderAndIdExterno(ev.provider(), ev.idExterno())) {
            return dto(null, null, ResultadoConciliacao.DUPLICADA, ev, "Transação já processada anteriormente.");
        }

        // 2) matching: ID de correlação primeiro; código de barras como fallback (SELECT ... FOR UPDATE)
        Optional<TituloFinanceiro> achado = Optional.empty();
        if (ev.identificador() != null && !ev.identificador().isBlank()) {
            achado = titulos.findByTipoAndIdentificadorPagamento(TipoTitulo.PAGAR, ev.identificador());
        }
        if (achado.isEmpty() && ev.codigoBarras() != null) {
            achado = titulos.findByTipoAndCodigoBarras(TipoTitulo.PAGAR, ev.codigoBarras());
        }

        TransacaoBancaria t = new TransacaoBancaria();
        t.setWebhookLogId(log.getId());
        t.setProvider(ev.provider());
        t.setIdExterno(ev.idExterno());
        t.setIdentificador(ev.identificador());
        t.setValor(ev.valor());
        t.setLiquidadoEm(ev.liquidadoEm());
        t.setComprovanteUrl(ev.comprovanteUrl());

        TituloFinanceiro titulo = achado.orElse(null);
        ResultadoConciliacao resultado;
        String msg;

        if (titulo == null) {
            resultado = ResultadoConciliacao.NAO_ENCONTRADA;
            msg = "Nenhum título a pagar corresponde ao identificador recebido.";
        } else if (!titulo.podeSerBaixado()) {
            resultado = ResultadoConciliacao.DUPLICADA;                  // já baixado por outra transação → revisão humana
            msg = "Título já estava baixado; possível pagamento em duplicidade.";
            t.setTituloId(titulo.getId());
        } else if (ev.valor().compareTo(titulo.getValor()) != 0) {       // compareTo: 100.0 == 100.00
            resultado = ResultadoConciliacao.DIVERGENTE;
            msg = "Valor pago (" + ev.valor() + ") difere do valor do título (" + titulo.getValor() + ").";
            titulo.marcarDivergente();                                   // NÃO baixa: vai para análise
            t.setTituloId(titulo.getId());
        } else {
            resultado = ResultadoConciliacao.CONCILIADA;
            msg = "Título baixado automaticamente.";
            t.setTituloId(titulo.getId());
        }

        t.setResultado(resultado);
        t.setMotivo(msg);
        t = transacoes.save(t);

        if (resultado == ResultadoConciliacao.CONCILIADA) {
            titulo.baixarPorConciliacao(ev.valor(), ev.liquidadoEm(), ev.provider(), t.getId(), ev.comprovanteUrl());
        }
        return dto(titulo, titulo == null ? null : titulo.getStatus(), resultado, ev, msg);
    }

    private static ConciliacaoEventoDTO dto(TituloFinanceiro t, StatusTitulo s, ResultadoConciliacao r,
                                            EventoPagamento ev, String msg) {
        return new ConciliacaoEventoDTO(t == null ? null : t.getId(), s, r, ev.provider(), ev.valor(), msg, OffsetDateTime.now());
    }
}