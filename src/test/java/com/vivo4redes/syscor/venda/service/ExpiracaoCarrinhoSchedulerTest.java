package com.vivo4redes.syscor.venda.service;

import com.vivo4redes.syscor.venda.repository.VendaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiracaoCarrinhoSchedulerTest {

    private static final Instant AGORA = Instant.parse("2026-11-03T12:00:00Z");
    private static final Instant LIMITE_24H = Instant.parse("2026-11-02T12:00:00Z");

    @Mock private VendaRepository vendaRepository;
    @Mock private VendaService vendaService;

    private ExpiracaoCarrinhoScheduler scheduler() {
        return new ExpiracaoCarrinhoScheduler(vendaRepository, vendaService, Duration.ofHours(24),
                Clock.fixed(AGORA, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("Busca carrinhos parados há mais de 24h e expira cada um com o mesmo limite")
    void expiraComLimiteDe24h() {
        when(vendaRepository.buscarIdsCarrinhosAbandonados(LIMITE_24H)).thenReturn(List.of(1L, 2L));

        scheduler().expirarCarrinhosAbandonados();

        verify(vendaService).expirarCarrinho(1L, LIMITE_24H);
        verify(vendaService).expirarCarrinho(2L, LIMITE_24H);
    }

    @Test
    @DisplayName("Falha em uma venda não interrompe as demais")
    void continuaAposFalha() {
        when(vendaRepository.buscarIdsCarrinhosAbandonados(LIMITE_24H)).thenReturn(List.of(1L, 2L));
        when(vendaService.expirarCarrinho(1L, LIMITE_24H)).thenThrow(new IllegalStateException("lock"));

        scheduler().expirarCarrinhosAbandonados();

        verify(vendaService).expirarCarrinho(2L, LIMITE_24H);
    }
}
