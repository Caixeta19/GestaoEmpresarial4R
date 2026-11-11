package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;
import com.vivo4redes.syscor.estoque.model.InventarioItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario.*;
import static org.assertj.core.api.Assertions.assertThat;

/** Tabela de classificação IQ09 x sistema x bipagem (docs/inventario_bipagem.md). */
class InventarioClassificadorTest {

    @Test
    @DisplayName("Esperado na IQ09 e no sistema: pendente, conferido ao bipar, FALTA se fechar sem achar")
    void iq09ESistema() {
        assertThat(classificar(iq09("ADEP").sistema("DISPONIVEL"), false)).isEqualTo(PENDENTE);
        assertThat(classificar(iq09("ADEP").sistema("DISPONIVEL").bipado(), false)).isEqualTo(CONFERIDO);
        assertThat(classificar(iq09("").sistema("DEFEITO").bipado(), false)).isEqualTo(CONFERIDO);
        assertThat(classificar(iq09("ADEP").sistema("RESERVADO").bipado(), false)).isEqualTo(CONFERIDO);
        assertThat(classificar(iq09("ADEP").sistema("DISPONIVEL"), true)).isEqualTo(FALTA);
    }

    @Test
    @DisplayName("Na IQ09 e bipado sem entrada no sistema da loja: SEM_ENTRADA_SISTEMA")
    void semEntrada() {
        assertThat(classificar(iq09("ADEP").bipado(), false)).isEqualTo(SEM_ENTRADA_SISTEMA);
        Item outraLoja = iq09("ADEP").bipado();
        outraLoja.item.setFilialSistema("CE - SOBRAL");
        outraLoja.item.setStatusSistema("DISPONIVEL");
        assertThat(classificar(outraLoja, false)).isEqualTo(SEM_ENTRADA_SISTEMA);
        assertThat(outraLoja.item.getObservacao()).contains("CE - SOBRAL");
    }

    @Test
    @DisplayName("Só no sistema: pendente, SOBRA_FISICA se bipado, SO_NO_SISTEMA se fechar sem achar")
    void soNoSistema() {
        assertThat(classificar(novo().sistema("DISPONIVEL"), false)).isEqualTo(PENDENTE);
        assertThat(classificar(novo().sistema("DISPONIVEL").bipado(), false)).isEqualTo(SOBRA_FISICA);
        assertThat(classificar(novo().sistema("DISPONIVEL"), true)).isEqualTo(SO_NO_SISTEMA);
    }

    @Test
    @DisplayName("Na IQ09 e VENDIDO no sistema: VENDIDO_EM_ESTOQUE, bipado ou não, e não bloqueia o fechamento")
    void vendidoEmEstoque() {
        Item vendido = iq09("ADEP");
        vendido.item.setStatusSistema("VENDIDO");
        assertThat(classificar(vendido, false)).isEqualTo(VENDIDO_EM_ESTOQUE);
        assertThat(classificar(vendido.bipado(), true)).isEqualTo(VENDIDO_EM_ESTOQUE);
    }

    @Test
    @DisplayName("IQ09 com status usuário fora do estoque da loja (RLIB/AECL/RESE): informativo; bipado = STATUS_SAP_DIVERGENTE")
    void statusUsuarioForaDaLoja() {
        assertThat(classificar(iq09("RLIB"), false)).isEqualTo(FORA_ESTOQUE_SAP);
        assertThat(classificar(iq09("RESE"), true)).isEqualTo(FORA_ESTOQUE_SAP);
        Item bipado = iq09("AECL").bipado();
        assertThat(classificar(bipado, false)).isEqualTo(STATUS_SAP_DIVERGENTE);
        assertThat(bipado.item.getObservacao()).contains("AECL");
    }

    @Test
    @DisplayName("Bipado fora de tudo: SOBRA_FISICA")
    void sobra() {
        assertThat(classificar(novo().bipado(), false)).isEqualTo(SOBRA_FISICA);
    }

    @Test
    @DisplayName("Só ADEP ou vazio contam como estoque da loja")
    void statusDeEstoqueDaLoja() {
        assertThat(InventarioClassificador.statusUsuarioDeEstoqueDaLoja("ADEP")).isTrue();
        assertThat(InventarioClassificador.statusUsuarioDeEstoqueDaLoja("")).isTrue();
        assertThat(InventarioClassificador.statusUsuarioDeEstoqueDaLoja(null)).isTrue();
        assertThat(InventarioClassificador.statusUsuarioDeEstoqueDaLoja(" adep ")).isTrue();
        assertThat(InventarioClassificador.statusUsuarioDeEstoqueDaLoja("RLIB")).isFalse();
        assertThat(InventarioClassificador.statusUsuarioDeEstoqueDaLoja("RESE")).isFalse();
        assertThat(InventarioClassificador.statusUsuarioDeEstoqueDaLoja("AECL")).isFalse();
    }

    // ---------------------------------------------------------------- montagem

    private static ResultadoConciliacaoInventario classificar(Item item, boolean fechado) {
        InventarioClassificador.classificar(item.item, fechado);
        return item.item.getResultado();
    }

    private static Item novo() {
        return new Item(InventarioItem.builder().serialNormalizado("356789012345678").build());
    }

    private static Item iq09(String statusUsuario) {
        Item i = novo();
        i.item.setCentroSap("039G");
        i.item.setStatusUsuarioSap(statusUsuario);
        i.item.setEsperadoIq09(InventarioClassificador.statusUsuarioDeEstoqueDaLoja(statusUsuario));
        return i;
    }

    private record Item(InventarioItem item) {
        Item sistema(String status) {
            item.setEsperadoSistema(true);
            item.setStatusSistema(status);
            item.setFilialSistema("CE - CANINDE");
            return this;
        }

        Item bipado() {
            item.setBipadoEm(Instant.now());
            return this;
        }
    }
}
