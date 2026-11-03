package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.exception.NegocioException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Ciclo da unidade no PDV: DISPONIVEL -> RESERVADO -> VENDIDO, com liberação de volta a DISPONIVEL. */
@ExtendWith(MockitoExtension.class)
class EstoqueGeralServiceReservaTest {

    private static final String IMEI = "356789012345678";

    @Mock private EstoqueGeralRepository repository;
    @Mock private LeitorTabular leitor;

    @InjectMocks
    private EstoqueGeralService service;

    @BeforeEach
    void setUp() {
        lenient().when(repository.save(any(EstoqueGeral.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("Reservar: DISPONIVEL -> RESERVADO, com lock e serial normalizado, sem data de venda")
    void reservaDisponivel() {
        EstoqueGeral item = unidade(StatusEstoque.DISPONIVEL);
        when(repository.findBySerialForUpdate(IMEI)).thenReturn(Optional.of(item));

        EstoqueGeral reservado = service.reservarNaVenda(" 3567 8901 2345678 ");

        assertThat(reservado.getStatus()).isEqualTo(StatusEstoque.RESERVADO);
        assertThat(reservado.getDataVenda()).isNull();
    }

    @ParameterizedTest
    @EnumSource(value = StatusEstoque.class, names = {"RESERVADO", "VENDIDO", "DEFEITO"})
    @DisplayName("Reservar unidade fora de DISPONIVEL é recusado")
    void reservaRecusada(StatusEstoque status) {
        when(repository.findBySerialForUpdate(IMEI)).thenReturn(Optional.of(unidade(status)));

        assertThatThrownBy(() -> service.reservarNaVenda(IMEI))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("não está disponível");
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("Reservar serial inexistente é recusado")
    void reservaSerialInexistente() {
        when(repository.findBySerialForUpdate(IMEI)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reservarNaVenda(IMEI))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("não foi encontrado");
    }

    @Test
    @DisplayName("Confirmar: RESERVADO -> VENDIDO, carimbando data_venda")
    void confirmaReservado() {
        EstoqueGeral item = unidade(StatusEstoque.RESERVADO);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));

        EstoqueGeral vendido = service.confirmarVenda(item);

        assertThat(vendido.getStatus()).isEqualTo(StatusEstoque.VENDIDO);
        assertThat(vendido.getDataVenda()).isNotNull();
    }

    @ParameterizedTest
    @EnumSource(value = StatusEstoque.class, names = {"DISPONIVEL", "VENDIDO", "DEFEITO"})
    @DisplayName("Confirmar unidade que não está RESERVADO é recusado")
    void confirmaRecusada(StatusEstoque status) {
        EstoqueGeral item = unidade(status);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.confirmarVenda(item))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("não está reservado");
        verify(repository, never()).save(any());
    }

    @ParameterizedTest
    @EnumSource(value = StatusEstoque.class, names = {"RESERVADO", "VENDIDO"})
    @DisplayName("Liberar: RESERVADO/VENDIDO -> DISPONIVEL e limpa data_venda")
    void liberaReservadoOuVendido(StatusEstoque status) {
        EstoqueGeral item = unidade(status);
        item.setDataVenda(LocalDateTime.now());
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));

        service.liberar(item);

        assertThat(item.getStatus()).isEqualTo(StatusEstoque.DISPONIVEL);
        assertThat(item.getDataVenda()).isNull();
    }

    @Test
    @DisplayName("Liberar unidade com DEFEITO é recusado (não volta à venda)")
    void liberaDefeitoRecusado() {
        EstoqueGeral item = unidade(StatusEstoque.DEFEITO);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));

        assertThatThrownBy(() -> service.liberar(item)).isInstanceOf(NegocioException.class);
        assertThat(item.getStatus()).isEqualTo(StatusEstoque.DEFEITO);
    }

    @Test
    @DisplayName("Liberar unidade já DISPONIVEL não altera nada")
    void liberaDisponivelSemEfeito() {
        EstoqueGeral item = unidade(StatusEstoque.DISPONIVEL);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(item));

        service.liberar(item);

        assertThat(item.getStatus()).isEqualTo(StatusEstoque.DISPONIVEL);
    }

    private static EstoqueGeral unidade(StatusEstoque status) {
        return EstoqueGeral.builder().id(1L).serial(IMEI).status(status).build();
    }
}
