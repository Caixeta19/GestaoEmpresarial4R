package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.financeiro.config.BbExtratoProperties;
import com.vivo4redes.syscor.financeiro.enums.StatusSincronizacao;
import com.vivo4redes.syscor.financeiro.model.ExtratoSincronizacao;
import com.vivo4redes.syscor.financeiro.repository.ExtratoSincronizacaoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Situação do canal bancário para o card da tela de contas a pagar: DESLIGADO (enabled=false), AGUARDANDO (nunca
 * sincronizou), CONECTADO (última rodada OK dentro de 2x o intervalo) ou COM_ERRO (última rodada com erro, ou a última
 * OK ficou velha demais: a sincronização parou).
 */
@Service
@RequiredArgsConstructor
public class CanalBancarioService {

    public record Canal(String situacao, OffsetDateTime ultimaSincronizacao) {
    }

    private final BbExtratoProperties props;
    private final ExtratoSincronizacaoRepository sincronizacoes;

    @Transactional(readOnly = true)
    public Canal situacao() {
        if (!props.isEnabled()) return new Canal("DESLIGADO", null);

        Optional<ExtratoSincronizacao> ultima = sincronizacoes.findFirstByOrderByFimDescIdDesc();
        if (ultima.isEmpty()) return new Canal("AGUARDANDO", null);

        OffsetDateTime ultimaOk = sincronizacoes.findFirstByStatusOrderByFimDescIdDesc(StatusSincronizacao.OK)
                .map(ExtratoSincronizacao::getFim).orElse(null);
        if (ultima.get().getStatus() == StatusSincronizacao.ERRO) return new Canal("COM_ERRO", ultimaOk);

        boolean velha = ultima.get().getFim().isBefore(OffsetDateTime.now()
                .minus(Duration.ofMillis(2 * props.getSincronizacaoMs())));
        return new Canal(velha ? "COM_ERRO" : "CONECTADO", ultimaOk);
    }
}
