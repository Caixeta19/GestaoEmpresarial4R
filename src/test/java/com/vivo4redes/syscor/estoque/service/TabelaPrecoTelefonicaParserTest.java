package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.service.ResultadoParseTabelaPreco.LinhaPlano;
import com.vivo4redes.syscor.estoque.service.ResultadoParseTabelaPreco.LinhaPreco;
import com.vivo4redes.syscor.exception.NegocioException;
import org.apache.poi.ss.usermodel.Workbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static com.vivo4redes.syscor.estoque.service.PlanilhaTelefonicaFixture.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TabelaPrecoTelefonicaParserTest {

    private final TabelaPrecoTelefonicaParser parser = new TabelaPrecoTelefonicaParser();

    @ParameterizedTest(name = "colunas deslocadas em {0}")
    @ValueSource(ints = {0, 3})
    @DisplayName("Encontra blocos e grupos pelo cabeçalho; lê preço cheio, PIX e 13x–21x")
    void leBlocosEGruposPeloCabecalho(int deslocamento) {
        ResultadoParseTabelaPreco r = parser.parsear(arquivo(deslocamento));

        LinhaPreco iphonePre = preco(r, IPHONE, TabelaPreco.REGULAR, GrupoPlano.PRE).orElseThrow();
        assertThat(iphonePre.aba()).isEqualTo("SMARTPHONES");
        assertThat(iphonePre.preco()).isEqualByComparingTo("12909.00");
        assertThat(iphonePre.precoPix()).isEqualByComparingTo("11618.10");
        assertThat(iphonePre.parcelas()).containsOnlyKeys(13, 21);
        assertThat(iphonePre.parcelas().get(13)).isEqualByComparingTo("13500.13");
        assertThat(iphonePre.parcelas().get(21)).isEqualByComparingTo("14000.21");

        // Cabeçalho "VIVO\nV" (quebra de linha) e 13x como texto.
        LinhaPreco iphoneVivoV = preco(r, IPHONE, TabelaPreco.REGULAR, GrupoPlano.VIVO_V).orElseThrow();
        assertThat(iphoneVivoV.preco()).isEqualByComparingTo("10149.00");
        assertThat(iphoneVivoV.precoPix()).isEqualByComparingTo("9134.10");
        assertThat(iphoneVivoV.parcelas().get(13)).isEqualByComparingTo("10500.10");
        assertThat(iphoneVivoV.parcelas().get(21)).isEqualByComparingTo("10900.99");

        assertThat(r.linhasPorAba()).containsEntry("SMARTPHONES", 2).containsEntry("ELETRÔNICOS_LP_Não Conectados", 1);
    }

    @Test
    @DisplayName("Bloco Renova ignora a coluna DESCONTO e lê os grupos")
    void blocoRenova() {
        ResultadoParseTabelaPreco r = parser.parsear(arquivo(0));

        LinhaPreco renovaPre = preco(r, IPHONE, TabelaPreco.RENOVA, GrupoPlano.PRE).orElseThrow();
        assertThat(renovaPre.preco()).isEqualByComparingTo("12000.00");
        assertThat(renovaPre.precoPix()).isEqualByComparingTo("10800.00");
        LinhaPreco renovaVivoV = preco(r, IPHONE, TabelaPreco.RENOVA, GrupoPlano.VIVO_V).orElseThrow();
        assertThat(renovaVivoV.preco()).isEqualByComparingTo("9500.00");
        assertThat(renovaVivoV.parcelas()).containsOnlyKeys(13);
        assertThat(r.precos()).noneMatch(p -> p.preco().compareTo(new BigDecimal("909.00")) == 0);
    }

    @Test
    @DisplayName("Ignora TABELA ANTERIOR e a coluna Oferta de Comunicação")
    void ignoraTabelaAnteriorEOferta() {
        ResultadoParseTabelaPreco r = parser.parsear(arquivo(0));

        assertThat(r.precos()).noneMatch(p -> p.preco().compareTo(new BigDecimal("99999.00")) == 0);
        assertThat(r.precos()).extracting(LinhaPreco::grupo)
                .containsOnly(GrupoPlano.PRE, GrupoPlano.VIVO_V, GrupoPlano.SEM_PLANO);
        assertThat(r.precos()).extracting(LinhaPreco::tabela).containsOnly(TabelaPreco.REGULAR, TabelaPreco.RENOVA);
    }

    @Test
    @DisplayName("Converte texto em formato brasileiro e pula linha sem nome ou sem preço numérico")
    void conversaoEPulos() {
        ResultadoParseTabelaPreco r = parser.parsear(arquivo(0));

        LinhaPreco galaxy = preco(r, GALAXY, TabelaPreco.REGULAR, GrupoPlano.PRE).orElseThrow();
        assertThat(galaxy.preco()).isEqualByComparingTo("1299.00");
        assertThat(galaxy.precoPix()).isEqualByComparingTo("1169.10");
        assertThat(galaxy.parcelas()).isNull();
        assertThat(preco(r, GALAXY, TabelaPreco.RENOVA, GrupoPlano.PRE)).isEmpty();
        assertThat(r.precos()).noneMatch(p -> p.nomeComercial().equals("Aparelho Sem Preço"));
        assertThat(r.precos()).allMatch(p -> p.preco().scale() == 2);
    }

    @Test
    @DisplayName("Aba sem plano: SEM_PLANO / REGULAR com PIX e parcelas")
    void abaSemPlano() {
        ResultadoParseTabelaPreco r = parser.parsear(arquivo(0));

        LinhaPreco fone = preco(r, FONE, TabelaPreco.REGULAR, GrupoPlano.SEM_PLANO).orElseThrow();
        assertThat(fone.preco()).isEqualByComparingTo("199.90");
        assertThat(fone.precoPix()).isEqualByComparingTo("179.91");
        assertThat(fone.parcelas()).containsOnlyKeys(13);
    }

    @Test
    @DisplayName("Lê vigência do ÍNDICE, planos (grupo e disponível para alta) e produtos")
    void indicePlanosEProdutos() {
        ResultadoParseTabelaPreco r = parser.parsear(arquivo(0));

        assertThat(r.vigencia()).isEqualTo(LocalDate.of(2026, 11, 1));
        assertThat(r.planos()).extracting(LinhaPlano::nomePlano, LinhaPlano::grupo, LinhaPlano::disponivelAlta)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(PLANO_CONTROLE, GrupoPlano.CONTROLE_ALTO_VALOR, true),
                        org.assertj.core.groups.Tuple.tuple(PLANO_VIVO_V, GrupoPlano.VIVO_V, true),
                        org.assertj.core.groups.Tuple.tuple(PLANO_PRE, GrupoPlano.PRE, false));
        LinhaPlano controle = r.planos().get(0);
        assertThat(controle.codSistema()).isEqualTo("CTL25");
        assertThat(controle.codSap()).isEqualTo("SAP25");
        assertThat(r.planos().get(2).codSap()).isNull();

        assertThat(r.produtos()).hasSize(3);
        assertThat(r.produtos().get(2).codSap()).isEqualTo("ACC-FONE");
        assertThat(r.produtos().get(2).nomeComercial()).isEqualTo(FONE);
    }

    @Test
    @DisplayName("Sem a aba PLANOS (obrigatória) a importação é recusada")
    void exigeAbaPlanos() throws Exception {
        try (Workbook wb = PlanilhaTelefonicaFixture.workbook(0, false)) {
            assertThatThrownBy(() -> parser.parsear(wb))
                    .isInstanceOf(NegocioException.class)
                    .hasMessageContaining("PLANOS");
        }
    }

    @Test
    @DisplayName("Abas opcionais ausentes não quebram a leitura")
    void abasOpcionaisAusentes() throws Exception {
        try (Workbook wb = PlanilhaTelefonicaFixture.workbook(0, true)) {
            wb.removeSheetAt(wb.getSheetIndex("ELETRÔNICOS_LP_Não Conectados"));
            wb.removeSheetAt(wb.getSheetIndex("ÍNDICE"));
            ResultadoParseTabelaPreco r = parser.parsear(wb);
            assertThat(r.vigencia()).isNull();
            assertThat(r.linhasPorAba()).containsOnlyKeys("SMARTPHONES");
        }
    }

    private static Optional<LinhaPreco> preco(ResultadoParseTabelaPreco r, String nome, TabelaPreco tabela, GrupoPlano grupo) {
        List<LinhaPreco> achados = r.precos().stream()
                .filter(p -> p.nomeComercial().equals(nome) && p.tabela() == tabela && p.grupo() == grupo)
                .toList();
        assertThat(achados).hasSizeLessThanOrEqualTo(1);
        return achados.stream().findFirst();
    }
}
