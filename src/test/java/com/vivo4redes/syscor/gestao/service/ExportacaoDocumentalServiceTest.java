package com.vivo4redes.syscor.gestao.service;

import com.vivo4redes.syscor.gestao.dto.request.FiltroDocumentalDTO;
import com.vivo4redes.syscor.gestao.repository.RelatorioDocumentalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class ExportacaoDocumentalServiceTest {

    @Test
    @DisplayName("Formato dos campos: data, data e hora, ponto decimal, Sim/Não, apóstrofos e texto sem ; nem quebra")
    void formatoDosCampos() {
        assertThat(ExportacaoDocumentalService.formatar(LocalDate.of(2026, 11, 2))).isEqualTo("02/11/2026");
        assertThat(ExportacaoDocumentalService.formatar(LocalDateTime.of(2026, 11, 2, 9, 7))).isEqualTo("02/11/2026 09:07");
        assertThat(ExportacaoDocumentalService.formatar(new BigDecimal("24.9"))).isEqualTo("24.90");
        assertThat(ExportacaoDocumentalService.formatar(new BigDecimal("1234.567"))).isEqualTo("1234.57");
        assertThat(ExportacaoDocumentalService.formatar(true)).isEqualTo("Sim");
        assertThat(ExportacaoDocumentalService.formatar(false)).isEqualTo("Não");
        assertThat(ExportacaoDocumentalService.formatar(null)).isEmpty();
        assertThat(ExportacaoDocumentalService.formatar(" a;b\r\n\"c\" ")).isEqualTo("a,b 'c'");
        assertThat(ExportacaoDocumentalService.campo("Sim Card", null)).isEqualTo("''");
        assertThat(ExportacaoDocumentalService.campo("Nº Protocolo GED(**)", "123")).isEqualTo("'123'");
        assertThat(ExportacaoDocumentalService.campo("Observação", "123")).isEqualTo("123");
    }

    @Test
    @DisplayName("Arquivo: emissão na hora de São Paulo, revenda da configuração e rodapé")
    @SuppressWarnings("unchecked")
    void preambuloERodape() {
        RelatorioDocumentalRepository repositorio = mock(RelatorioDocumentalRepository.class);
        doAnswer(inv -> {
            Object[] linha = new Object[RelatorioDocumentalRepository.COLUNAS.size()];
            linha[0] = "Loja Centro";
            ((Consumer<Object[]>) inv.getArgument(1)).accept(linha);
            return null;
        }).when(repositorio).percorrer(any(), any());
        ZoneId zona = ZoneId.of("America/Sao_Paulo");
        Clock relogio = Clock.fixed(ZonedDateTime.of(2026, 11, 10, 9, 37, 0, 0, zona).toInstant(), zona);
        var servico = new ExportacaoDocumentalService(repositorio, "REVENDA X", relogio);

        var arquivo = servico.exportar(new FiltroDocumentalDTO(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 10),
                null, null, null, null, null, null, null, null, null, false));

        String texto = new String(arquivo.conteudo(), ExportacaoDocumentalService.CHARSET);
        String[] linhas = texto.split("\n", -1);
        assertThat(arquivo.nome()).isEqualTo("gestao_documental.csv");
        assertThat(linhas[0]).isEqualTo("Data da Emissão do relatório;10/11/2026 09:37");
        assertThat(linhas[2]).isEqualTo("Revenda: REVENDA X");
        assertThat(linhas[5]).startsWith("Loja Centro;;").contains(";'';");
        assertThat(linhas[5].split(";", -1)).hasSize(55);
        assertThat(linhas).endsWith("", "", "(*) Campos obrigatórios;", "(**) Campos que podem ser alterados;", "");
    }
}
