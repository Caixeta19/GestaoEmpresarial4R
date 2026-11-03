package com.vivo4redes.syscor.estoque.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** Contrato do GET /estoque-geral/consolidado durante a transição avariado -> defeito. */
class EstoqueConsolidadoModeloDTOTest {

    @Test
    @DisplayName("JSON traz 'defeito' e o alias temporário 'avariado' com o mesmo valor")
    void serializaDefeitoEAliasAvariado() throws Exception {
        var dto = new EstoqueConsolidadoModeloDTO("Galaxy A15", "Samsung", "PRODUTO_VIVO",
                5L, 1L, 3L, new BigDecimal("999.90"));

        JsonNode json = new ObjectMapper().valueToTree(dto);

        assertThat(json.get("defeito").asLong()).isEqualTo(3L);
        assertThat(json.get("avariado").asLong()).isEqualTo(3L);
        assertThat(json.get("disponivel").asLong()).isEqualTo(5L);
        assertThat(json.get("reservado").asLong()).isEqualTo(1L);
    }
}
