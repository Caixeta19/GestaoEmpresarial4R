package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.response.SerialDisponivelResponseDTO;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import com.vivo4redes.syscor.estoque.repository.EstoqueGeralRepository;
import com.vivo4redes.syscor.util.EstoqueUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Importação do relatório Vivo: codigo_acessorio com marcador de vazio vira null (o item é aparelho). */
@ExtendWith(MockitoExtension.class)
class EstoqueGeralServiceImportacaoTest {

    private static final String IMEI = "356789012345678";

    @Mock private EstoqueGeralRepository repository;
    @Mock private LeitorTabular leitor;

    @InjectMocks
    private EstoqueGeralService service;

    @ParameterizedTest(name = "codigo_acessorio = [{0}]")
    @ValueSource(strings = {"''", "\"\"", "'", "\"", "-", "null", "NULL", " '' ", ""})
    @DisplayName("codigo_acessorio com marcador de vazio grava null e o item é aparelho")
    void marcadorDeVazioViraNull(String codigoAcessorio) {
        EstoqueGeral gravado = importar(codigoAcessorio);

        assertThat(gravado.getSerial()).isEqualTo(IMEI);
        assertThat(gravado.getCodigoAcessorio()).isNull();
        assertThat(EstoqueUtil.ehAcessorio(gravado)).isFalse();
        assertThat(SerialDisponivelResponseDTO.from(gravado).categoria()).isEqualTo("PRODUTO_VIVO");
    }

    @Test
    @DisplayName("codigo_acessorio real continua gravado e o item é acessório")
    void codigoRealContinuaAcessorio() {
        EstoqueGeral gravado = importar(" ACC123 ");

        assertThat(gravado.getCodigoAcessorio()).isEqualTo("ACC123");
        assertThat(EstoqueUtil.ehAcessorio(gravado)).isTrue();
        assertThat(SerialDisponivelResponseDTO.from(gravado).categoria()).isEqualTo("ACESSORIO");
    }

    @SuppressWarnings("unchecked")
    private EstoqueGeral importar(String codigoAcessorio) {
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("imei", IMEI);
        valores.put("modelo", "Galaxy A15");
        valores.put("codigo acessorio", codigoAcessorio);
        LeitorTabular.Tabela tabela = new LeitorTabular.Tabela(
                Set.of("imei", "modelo", "codigo acessorio"), List.of(new LeitorTabular.Linha(2, valores)));

        when(leitor.ler(any())).thenReturn(tabela);
        when(repository.findBySerialIn(anyList())).thenReturn(List.of());

        service.importarRelatorioVivo(new MockMultipartFile("arquivo", "relatorio.csv", "text/csv", new byte[0]));

        ArgumentCaptor<List<EstoqueGeral>> salvos = ArgumentCaptor.forClass(List.class);
        verify(repository).saveAll(salvos.capture());
        assertThat(salvos.getValue()).hasSize(1);
        return salvos.getValue().get(0);
    }
}
