package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.enums.OrigemSku;
import com.vivo4redes.syscor.estoque.model.InventarioSku;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static com.vivo4redes.syscor.estoque.enums.ResultadoSku.*;
import static org.assertj.core.api.Assertions.assertThat;

class InventarioSkuClassificadorTest {

    private static InventarioSku sku(String livre, int iq09, int sistema, int bipada) {
        return InventarioSku.builder().materialNormalizado("A1").temLinhaMb52(true)
                .qtdMb52Livre(new BigDecimal(livre)).qtdIq09(iq09).qtdSistema(sistema).qtdBipada(bipada)
                .origemSku(OrigemSku.IQ09).build();
    }

    private static InventarioSku classificado(InventarioSku s) {
        InventarioSkuClassificador.classificar(s);
        return s;
    }

    @Test
    @DisplayName("Um SKU de cada resultado")
    void umDeCada() {
        assertThat(classificado(sku("3", 3, 3, 0)).getResultado()).isEqualTo(CONFERIDO);
        assertThat(classificado(sku("3", 3, 3, 3)).getResultado()).isEqualTo(CONFERIDO);
        assertThat(classificado(sku("3", 2, 3, 0)).getResultado()).isEqualTo(MB52_DIFERE_IQ09);
        assertThat(classificado(sku("4", 4, 3, 0)).getResultado()).isEqualTo(SAP_ACIMA_SISTEMA);
        assertThat(classificado(sku("2", 2, 3, 0)).getResultado()).isEqualTo(SAP_ABAIXO_SISTEMA);
        assertThat(classificado(sku("2", 2, 0, 0)).getResultado()).isEqualTo(SO_NO_SAP);
        assertThat(classificado(sku("0", 0, 2, 0)).getResultado()).isEqualTo(SO_NO_SISTEMA);
        assertThat(classificado(sku("3", 3, 3, 2)).getResultado()).isEqualTo(CONTAGEM_DIVERGENTE);

        InventarioSku semSku = InventarioSku.builder().qtdSistema(2).qtdBipada(1).build();
        assertThat(classificado(semSku).getResultado()).isEqualTo(SKU_NAO_IDENTIFICADO);
    }

    @Test
    @DisplayName("Prioridade: SO_NO_SAP antes da contagem; contagem antes de SAP x sistema; SAP x sistema antes de IQ09")
    void prioridade() {
        assertThat(classificado(sku("2", 0, 0, 1)).getResultado()).isEqualTo(SO_NO_SAP);
        assertThat(classificado(sku("4", 1, 3, 1)).getResultado()).isEqualTo(CONTAGEM_DIVERGENTE);
        assertThat(classificado(sku("4", 1, 3, 0)).getResultado()).isEqualTo(SAP_ACIMA_SISTEMA);
    }

    @Test
    @DisplayName("Acessório (não serializado no SAP) não cai em MB52_DIFERE_IQ09; contagem manual vale no lugar da bipagem")
    void acessorio() {
        InventarioSku cabo = sku("5", 0, 5, 1);
        cabo.setOrigemSku(OrigemSku.ACESSORIO);
        assertThat(classificado(cabo).getResultado()).as("bipou 1 de 5").isEqualTo(CONTAGEM_DIVERGENTE);

        cabo.setQtdContagemManual(new BigDecimal("5"));
        assertThat(classificado(cabo).getResultado()).isEqualTo(CONFERIDO);
        assertThat(cabo.getDifContagem()).isEqualByComparingTo("0");

        cabo.setQtdContagemManual(new BigDecimal("4"));
        assertThat(classificado(cabo).getResultado()).isEqualTo(CONTAGEM_DIVERGENTE);
        assertThat(cabo.getDifContagem()).isEqualByComparingTo("-1");
    }

    @Test
    @DisplayName("Diferenças e valor estimado; sem contagem, diferença de contagem nula")
    void diferencas() {
        InventarioSku s = sku("5", 4, 3, 0);
        s.setValorUnitarioSap(new BigDecimal("10.5"));
        classificado(s);

        assertThat(s.getDifSapSistema()).isEqualByComparingTo("2");
        assertThat(s.getDifSapIq09()).isEqualByComparingTo("1");
        assertThat(s.getDifContagem()).isNull();
        assertThat(s.getValorDiferenca()).isEqualByComparingTo("21.00");
    }

    @Test
    @DisplayName("Outros estoques SAP não entram na conta; trânsito que fecha a diferença aparece na observação")
    void transito() {
        InventarioSku s = sku("2", 2, 3, 0);
        s.setQtdMb52Transito(BigDecimal.ONE);
        s.setQtdMb52Bloqueado(BigDecimal.TEN);
        classificado(s);

        assertThat(s.getResultado()).isEqualTo(SAP_ABAIXO_SISTEMA);
        assertThat(s.getObservacao()).contains("trânsito");
    }

    @Test
    @DisplayName("Sem linha na MB52 e sistema > 0: SO_NO_SISTEMA com observação própria")
    void semLinhaMb52() {
        InventarioSku s = sku("0", 0, 1, 0);
        s.setTemLinhaMb52(false);
        assertThat(classificado(s).getObservacao()).startsWith("Sem linha na MB52");
    }

    @Test
    @DisplayName("SKU resolvido pela tabela de preços aparece como estimativa na observação")
    void estimado() {
        InventarioSku s = sku("1", 0, 1, 0);
        s.setUnidadesEstimadas(1);
        assertThat(classificado(s).getObservacao()).contains("estimado pela tabela de preços");
    }

    @Test
    @DisplayName("Resolvedor: acessório → IQ09 → tabela de preços (modelo + cor, Cod_Sap único) → não identificado")
    void resolvedor() {
        Map<String, List<String[]>> tabela = Map.of(
                "IPHONE 15", List.of(new String[]{"PRETO", "111"}, new String[]{"AZUL", "222"}),
                "MOTO G", List.<String[]>of(new String[]{"PRETO", "333"}));

        assertThat(InventarioSkuService.resolver("999", "0000777", "IPHONE 15", "Preto", tabela))
                .isEqualTo(new InventarioSkuService.SkuResolvido("777", OrigemSku.ACESSORIO));
        assertThat(InventarioSkuService.resolver("000999", null, "IPHONE 15", "Preto", tabela))
                .isEqualTo(new InventarioSkuService.SkuResolvido("999", OrigemSku.IQ09));
        assertThat(InventarioSkuService.resolver(null, null, "iPhone 15", "preto", tabela).sku()).isEqualTo("111");
        assertThat(InventarioSkuService.resolver(null, null, "Moto G", null, tabela).sku()).isEqualTo("333");
        assertThat(InventarioSkuService.resolver(null, null, "iPhone 15", null, tabela).origem())
                .as("duas cores, sem cor no estoque: ambíguo").isEqualTo(OrigemSku.NAO_IDENTIFICADO);
        assertThat(InventarioSkuService.resolver(null, null, "iPhone 15", "Verde", tabela).sku()).isNull();
        assertThat(InventarioSkuService.resolver(null, null, null, null, tabela).origem()).isEqualTo(OrigemSku.NAO_IDENTIFICADO);
    }

    @Test
    @DisplayName("Normalização do SKU: zeros à esquerda só quando numérico")
    void normalizacao() {
        assertThat(SkuInventario.normalizar("000000000012345")).isEqualTo("12345");
        assertThat(SkuInventario.normalizar(" 12345 ")).isEqualTo("12345");
        assertThat(SkuInventario.normalizar("0abc 12")).isEqualTo("0ABC12");
        assertThat(SkuInventario.normalizar("'0000")).isEqualTo("0");
        assertThat(SkuInventario.normalizar(null)).isEmpty();
    }
}
