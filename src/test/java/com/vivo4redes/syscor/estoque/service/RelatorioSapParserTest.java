package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.Iq09Sintetica;
import com.vivo4redes.syscor.exception.NegocioException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** IQ09 no layout do export real do SAP (cabeçalho com "Nº de série", "Status usuário" etc.), sempre sintética. */
class RelatorioSapParserTest {

    private final RelatorioSapParser parser = new RelatorioSapParser();

    @Test
    @DisplayName("Cabeçalho real da IQ09: reconhece Nº de série, Texto breve, Status do sistema/usuário, Ctg e data do Excel")
    void cabecalhoReal() {
        List<LinhaSapDTO> linhas = parser.parsear(Iq09Sintetica.nova()
                .linha("220181370043758", "039G", "ADEP", "DEPS NREM")
                .arquivo());

        assertThat(linhas).singleElement().satisfies(l -> {
            assertThat(l.serial()).isEqualTo("220181370043758");
            assertThat(l.material()).isEqualTo("22018137");
            assertThat(l.denominacao()).startsWith("APARELHO SINTETICO");
            assertThat(l.centro()).isEqualTo("039G");
            assertThat(l.deposito()).isEqualTo("RPAR");
            assertThat(l.tipoEstoque()).isEqualTo("01");
            assertThat(l.statusSistema()).isEqualTo("DEPS NREM");
            assertThat(l.statusUsuario()).isEqualTo("ADEP");
            assertThat(l.categoriaEquipamento()).isEqualTo("S");
            assertThat(l.modificadoEm()).isEqualTo(LocalDate.of(2026, 11, 3));
            assertThat(l.modificadoPor()).isEqualTo("GOT00000001");
        });
    }

    @Test
    @DisplayName("Nº de série gravado como número vira texto exato, sem notação científica")
    void serialNumerico() {
        List<LinhaSapDTO> linhas = parser.parsear(Iq09Sintetica.nova().linhaComSerialNumerico(356789012345678L, "039G").arquivo());

        assertThat(linhas.get(0).serial()).isEqualTo("356789012345678");
    }

    @Test
    @DisplayName("Nº de série em notação científica (2.20181E+14) recusa o arquivo com mensagem clara")
    void notacaoCientifica() {
        MockMultipartFile arquivo = Iq09Sintetica.nova().linha("2.20181E+14", "039G").arquivo();

        assertThatThrownBy(() -> parser.parsear(arquivo))
                .isInstanceOf(NegocioException.class)
                .hasMessageContaining("notação científica")
                .hasMessageContaining("como texto");
    }

    @Test
    @DisplayName("Linha sem Nº de série vem com serial vazio (o inventário conta como \"sem serial\")")
    void semSerial() {
        List<LinhaSapDTO> linhas = parser.parsear(Iq09Sintetica.nova().linha("", "039G").linha("123456789012345", "039G").arquivo());

        assertThat(linhas).extracting(LinhaSapDTO::serial).containsExactly("", "123456789012345");
    }

    @Test
    @DisplayName("CSV em Latin-1 com ; e variações do cabeçalho (N° de serie, Status usuario)")
    void csvLatin1() {
        String csv = "Material;Texto breve material;N° de série;Centro;Depósito;Status do sistema;Modificado em;Status usuário\n"
                + "22018137;CABO TESTE;'220181370043758;039g;rpar;DEPS;03.11.2026;rese\n";
        List<LinhaSapDTO> linhas = parser.parsear(new MockMultipartFile("arquivo", "iq09.csv", "text/csv",
                csv.getBytes(StandardCharsets.ISO_8859_1)));

        assertThat(linhas).singleElement().satisfies(l -> {
            assertThat(l.serial()).isEqualTo("'220181370043758");
            assertThat(l.centro()).isEqualTo("039G");
            assertThat(l.statusUsuario()).isEqualTo("RESE");
            assertThat(l.modificadoEm()).isEqualTo(LocalDate.of(2026, 11, 3));
        });
    }

    @Test
    @DisplayName("Sem a coluna de série: erro listando as colunas esperadas")
    void semColunaSerial() {
        MockMultipartFile csv = new MockMultipartFile("arquivo", "iq09.csv", "text/csv",
                "Material;Centro\n1;039G\n".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> parser.parsear(csv)).isInstanceOf(NegocioException.class).hasMessageContaining("Nº de série");
    }

    @Test
    @DisplayName("Normalização do cabeçalho: NFKD, sem º°.() e espaços duplicados")
    void normalizarCabecalho() {
        assertThat(RelatorioSapParser.normalizarCabecalho("Nº de série")).isEqualTo("no de serie");
        assertThat(RelatorioSapParser.normalizarCabecalho("N° de série")).isEqualTo("n de serie");
        assertThat(RelatorioSapParser.normalizarCabecalho("Tipo estoque (reg.principal)")).isEqualTo("tipo estoque reg principal");
        assertThat(RelatorioSapParser.normalizarCabecalho("Ctg.equipamento")).isEqualTo("ctg equipamento");
        assertThat(RelatorioSapParser.normalizarCabecalho("Status  usuário")).isEqualTo("status usuario");
    }

    @Test
    @DisplayName("Serial com apóstrofo, espaços e zeros à esquerda (16+ dígitos) normaliza para a mesma chave")
    void normalizarSerial() {
        assertThat(SerialInventario.normalizar(" '35 6789 0123 45678 ")).isEqualTo("356789012345678");
        assertThat(SerialInventario.normalizar("0000194252156926")).isEqualTo("194252156926");
        assertThat(SerialInventario.normalizar("356789012345678")).isEqualTo("356789012345678");
        assertThat(SerialInventario.normalizar("abc-12")).isEqualTo("ABC-12");
    }
}
