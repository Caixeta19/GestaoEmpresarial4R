package com.vivo4redes.syscor.estoque.service.diagnostico;

import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Monta o pedido real do SDK (schema da resposta, modelo, contexto), sem chamar a API. */
class ClaudeDiagnosticoIaClientTest {

    private final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule());

    @Test
    @DisplayName("Sem chave: indisponível. Com chave: disponível")
    void disponibilidade() {
        assertThat(cliente("").disponivel()).isFalse();
        assertThat(cliente("  ").disponivel()).isFalse();
        assertThat(cliente("sk-ant-teste").disponivel()).isTrue();
    }

    @Test
    @DisplayName("Pedido: modelo configurado, prompt com as causas, contexto em JSON sem dados de cliente e saída estruturada")
    void montaPedido() {
        ItemParaDiagnostico item = new ItemParaDiagnostico("356789012345678", "FALTA", "Na IQ09 da loja e não localizado.",
                "22018137", "APARELHO SINTETICO", true, "DEPS NREM", "ADEP", LocalDate.of(2026, 11, 3), "GOT00000001",
                "DISPONIVEL", "CE - CANINDE", LocalDate.of(2026, 10, 1), "CE - CANINDE", List.of("041A/RPAR (ADEP)"),
                false, "Não localizado fisicamente", new ItemParaDiagnostico.UltimaVenda(1234567L, LocalDate.of(2026, 11, 5), "CONCLUIDA"));

        StructuredMessageCreateParams<RespostaDiagnosticoIa> pedido = cliente("sk-ant-teste").montarRequisicao(List.of(item));
        String texto = pedido.rawParams().toString();

        assertThat(texto).contains("claude-opus-5-5");
        assertThat(texto).contains("356789012345678").contains("041A/RPAR (ADEP)").contains("1234567");
        assertThat(texto).contains("VENDIDO_SEM_BAIXA_SAP").contains("RESERVADO_RESE").contains("OUTRA");
        assertThat(texto).contains("POSSIVEL_PERDA_OU_FURTO");
        assertThat(texto).doesNotContainIgnoringCase("cpf").doesNotContainIgnoringCase("telefone");
    }

    private ClaudeDiagnosticoIaClient cliente(String chave) {
        return new ClaudeDiagnosticoIaClient(json, chave, "claude-opus-5-5", Duration.ofSeconds(30), 16000);
    }
}
