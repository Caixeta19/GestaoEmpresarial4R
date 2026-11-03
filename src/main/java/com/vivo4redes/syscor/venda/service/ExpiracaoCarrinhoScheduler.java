package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.venda.repository.VendaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Cancela carrinhos (vendas ABERTA) sem atividade há mais de {@code syscor.carrinho.expiracao}
 * e devolve as unidades RESERVADO ao estoque. Cada venda é tratada na própria transação
 * ({@link VendaService#expirarCarrinho}); a falha de uma não interrompe as demais.
 */
@Component
public class ExpiracaoCarrinhoScheduler {

    private static final Logger log = LoggerFactory.getLogger(ExpiracaoCarrinhoScheduler.class);

    private final VendaRepository vendaRepository;
    private final VendaService vendaService;
    private final Duration expiracao;
    private final Clock clock;

    @Autowired
    public ExpiracaoCarrinhoScheduler(VendaRepository vendaRepository,
                                      VendaService vendaService,
                                      @Value("${syscor.carrinho.expiracao:24h}") Duration expiracao) {
        this(vendaRepository, vendaService, expiracao, Clock.systemUTC());
    }

    ExpiracaoCarrinhoScheduler(VendaRepository vendaRepository, VendaService vendaService,
                               Duration expiracao, Clock clock) {
        this.vendaRepository = vendaRepository;
        this.vendaService = vendaService;
        this.expiracao = expiracao;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${syscor.carrinho.verificacao-ms:300000}",
               initialDelayString = "${syscor.carrinho.verificacao-ms:300000}")
    public void expirarCarrinhosAbandonados() {
        Instant limite = clock.instant().minus(expiracao);
        List<Long> ids = vendaRepository.buscarIdsCarrinhosAbandonados(limite);
        int expirados = 0;
        for (Long id : ids) {
            try {
                if (vendaService.expirarCarrinho(id, limite)) expirados++;
            } catch (RuntimeException ex) {
                log.warn("Falha ao expirar o carrinho da venda {}: {}", id, ex.getMessage(), ex);
            }
        }
        if (expirados > 0) {
            log.info("Expiração de carrinhos: {} venda(s) ABERTA cancelada(s) por inatividade (limite {}).", expirados, limite);
        }
    }
}
