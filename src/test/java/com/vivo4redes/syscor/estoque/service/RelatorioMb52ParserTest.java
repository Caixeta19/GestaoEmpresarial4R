package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.Mb52Sintetica;
import com.vivo4redes.syscor.exception.NegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelatorioMb52ParserTest {

    private final RelatorioMb52Parser parser = new RelatorioMb52Parser();

    @Test
    @DisplayName("Cabeçalho em outra ordem e depois de uma linha de título: colunas pelo nome")
    void cabecalhoForaDeOrdem() {
        var arquivo = Mb52Sintetica.nova()
                .titulo("Lista de estoques em depósito")
                .cabecalho("Centro", "Utilização livre", "Depósito", "Material", "UMB", "Bloqueado")
                .bruta("039G", 3, "RPAR", "ABC123", "UN", 1)
                .arquivo();

        LinhaMb52DTO l = parser.parsear(arquivo).linhas().get(0);

        assertThat(l.material()).isEqualTo("ABC123");
        assertThat(l.centro()).isEqualTo("039G");
        assertThat(l.deposito()).isEqualTo("RPAR");
        assertThat(l.livre()).isEqualByComparingTo("3");
        assertThat(l.bloqueado()).isEqualByComparingTo("1");
        assertThat(l.transito()).isEqualByComparingTo("0");
        assertThat(l.valorLivre()).isNull();
    }

    @Test
    @DisplayName("Linhas de total, em branco e cabeçalho repetido são ignoradas e contadas")
    void totaisEBrancos() {
        var arquivo = Mb52Sintetica.nova()
                .linha("ABC123", "039G", "RPAR", 2, 100)
                .bruta("", "", "039G", "", "", 2, 0, 0, 0, 100)
                .bruta("Total", "", "", "", "", 2, 0, 0, 0, 100)
                .bruta("*", "", "", "", "", 2)
                .bruta((Object[]) Mb52Sintetica.CABECALHO)
                .bruta()
                .linha("XYZ9", "039G", "RPAR", 1, 50)
                .arquivo();

        LinhaMb52DTO.Leitura leitura = parser.parsear(arquivo);

        assertThat(leitura.linhas()).extracting(LinhaMb52DTO::material).containsExactly("ABC123", "XYZ9");
        assertThat(leitura.ignoradas()).isEqualTo(4);
    }

    @Test
    @DisplayName("Formatos SAP em texto: sinal no fim, vírgula decimal com milhar e material com zeros à esquerda")
    void formatosSap() {
        var arquivo = Mb52Sintetica.nova()
                .bruta("000000000012345", "CABO", "039G", "RPAR", "UN", "5-", "1.234,000", "0", "2", "1.500,50")
                .arquivo();

        LinhaMb52DTO l = parser.parsear(arquivo).linhas().get(0);

        assertThat(l.material()).isEqualTo("000000000012345");
        assertThat(SkuInventario.normalizar(l.material())).isEqualTo("12345");
        assertThat(l.livre()).isEqualByComparingTo("-5");
        assertThat(l.qualidade()).isEqualByComparingTo("1234");
        assertThat(l.transito()).isEqualByComparingTo("2");
        assertThat(l.valorLivre()).isEqualByComparingTo(new BigDecimal("1500.50"));
    }

    @Test
    @DisplayName("Números de texto: '1.234' é milhar, '1,5' e '1.5' são decimais")
    void numerosDeTexto() {
        assertThat(RelatorioMb52Parser.numeroDeTexto("1.234", "x", 1)).isEqualByComparingTo("1234");
        assertThat(RelatorioMb52Parser.numeroDeTexto("1,5", "x", 1)).isEqualByComparingTo("1.5");
        assertThat(RelatorioMb52Parser.numeroDeTexto("1.5", "x", 1)).isEqualByComparingTo("1.5");
        assertThat(RelatorioMb52Parser.numeroDeTexto("1,234.50", "x", 1)).isEqualByComparingTo("1234.5");
        assertThat(RelatorioMb52Parser.numeroDeTexto("-3", "x", 1)).isEqualByComparingTo("-3");
        assertThat(RelatorioMb52Parser.numeroDeTexto("", "x", 1)).isNull();
        assertThatThrownBy(() -> RelatorioMb52Parser.numeroDeTexto("abc", "Utilização livre", 7))
                .isInstanceOf(NegocioException.class).hasMessageContaining("Linha 7").hasMessageContaining("Utilização livre");
    }

    @Test
    @DisplayName("Coluna obrigatória faltando: 422 com as colunas esperadas e as encontradas")
    void colunaObrigatoriaFaltando() {
        var arquivo = Mb52Sintetica.nova().cabecalho("Material", "Centro", "Depósito").bruta("A", "039G", "RPAR").arquivo();

        assertThatThrownBy(() -> parser.parsear(arquivo))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("Colunas esperadas")
                .hasMessageContaining("Utilização livre")
                .hasMessageContaining("Colunas encontradas: Material, Centro, Depósito");
    }

    @Test
    @DisplayName("Quantidade fracionada em unidade de contagem (UN): 422 com a linha; em KG é aceita")
    void fracionado() {
        var emUn = Mb52Sintetica.nova().bruta("A", "A", "039G", "RPAR", "UN", 1.5).arquivo();
        assertThatThrownBy(() -> parser.parsear(emUn))
                .isInstanceOf(NegocioException.class).hasMessageContaining("Linha 2").hasMessageContaining("fracionada");

        var emKg = Mb52Sintetica.nova().bruta("A", "A", "039G", "RPAR", "KG", 1.5).arquivo();
        assertThat(parser.parsear(emKg).linhas().get(0).livre()).isEqualByComparingTo("1.5");
    }

    @Test
    @DisplayName("CSV com ; também é aceito")
    void csv() {
        String conteudo = "Material;Centro;Depósito;Utilização livre\nA1;039G;RPAR;3\n";
        var arquivo = new MockMultipartFile("arquivo", "mb52.csv", "text/csv", conteudo.getBytes(StandardCharsets.UTF_8));

        List<LinhaMb52DTO> linhas = parser.parsear(arquivo).linhas();

        assertThat(linhas).hasSize(1);
        assertThat(linhas.get(0).livre()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("Arquivo vazio: 422")
    void vazio() {
        var arquivo = new MockMultipartFile("arquivo", "mb52.xlsx", "application/octet-stream", new byte[0]);
        assertThatThrownBy(() -> parser.parsear(arquivo)).isInstanceOf(NegocioException.class);
    }
}
