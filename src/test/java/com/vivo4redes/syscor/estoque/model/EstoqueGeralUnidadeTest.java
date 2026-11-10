package com.vivo4redes.syscor.estoque.model;

import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.exception.NegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Acessório com um serial por SKU e quantidade N: reserva, venda e devolução de uma unidade por vez. */
class EstoqueGeralUnidadeTest {

    private static EstoqueGeral linha(int quantidade) {
        return EstoqueGeral.builder().id(1L).serial("13025").codigoAcessorio("'13025'").quantidade(quantidade).build();
    }

    @Test
    @DisplayName("Reservar unidades: a linha fica DISPONIVEL até a última e então vira RESERVADO")
    void reservaAteAUltima() {
        EstoqueGeral l = linha(2);

        l.reservarUnidade();
        assertThat(l.getStatus()).isEqualTo(StatusEstoque.DISPONIVEL);
        assertThat(l.quantidadeLivre()).isEqualTo(1);

        l.reservarUnidade();
        assertThat(l.getStatus()).isEqualTo(StatusEstoque.RESERVADO);
        assertThat(l.quantidadeLivre()).isZero();

        assertThatThrownBy(l::reservarUnidade).isInstanceOf(NegocioException.class);
    }

    @Test
    @DisplayName("Confirmar a venda de uma unidade tira da quantidade; a última vira VENDIDO")
    void confirmaUnidade() {
        EstoqueGeral l = linha(2);
        l.reservarUnidade();
        l.confirmarVendaUnidade();

        assertThat(l.getQuantidade()).isEqualTo(1);
        assertThat(l.getQuantidadeReservada()).isZero();
        assertThat(l.getStatus()).isEqualTo(StatusEstoque.DISPONIVEL);

        l.reservarUnidade();
        l.confirmarVendaUnidade();
        assertThat(l.getQuantidade()).isZero();
        assertThat(l.getStatus()).isEqualTo(StatusEstoque.VENDIDO);
        assertThat(l.getDataVenda()).isNotNull();
    }

    @Test
    @DisplayName("Liberar a reserva devolve a unidade livre; devolver a vendida aumenta a quantidade")
    void liberarEDevolver() {
        EstoqueGeral l = linha(1);
        l.reservarUnidade();
        l.liberarUnidadeReservada();
        assertThat(l.getStatus()).isEqualTo(StatusEstoque.DISPONIVEL);
        assertThat(l.quantidadeLivre()).isEqualTo(1);

        l.reservarUnidade();
        l.confirmarVendaUnidade();
        l.devolverUnidadeVendida();
        assertThat(l.getQuantidade()).isEqualTo(1);
        assertThat(l.getStatus()).isEqualTo(StatusEstoque.DISPONIVEL);
    }

    @Test
    @DisplayName("Com unidade presa em carrinho, a linha inteira não pode ser reservada")
    void linhaInteiraComUnidadeReservada() {
        EstoqueGeral l = linha(3);
        l.reservarUnidade();

        assertThatThrownBy(l::reservar).isInstanceOf(NegocioException.class);
    }

    @Test
    @DisplayName("Reserva antiga da linha inteira (sem contador) ainda é confirmada e liberada")
    void reservaAntigaDaLinhaInteira() {
        EstoqueGeral confirmar = linha(1);
        confirmar.reservar();
        confirmar.confirmarVendaUnidade();
        assertThat(confirmar.getStatus()).isEqualTo(StatusEstoque.VENDIDO);

        EstoqueGeral soltar = linha(1);
        soltar.reservar();
        soltar.liberarUnidadeReservada();
        assertThat(soltar.getStatus()).isEqualTo(StatusEstoque.DISPONIVEL);
    }
}
