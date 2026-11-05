package com.vivo4redes.syscor.venda.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CondicaoPagamentoRequestDTOTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    @DisplayName("Aceita os dois nomes do campo: condicao e condicaoPagamento")
    void aceitaOsDoisNomes() throws Exception {
        assertThat(mapper.readValue("{\"condicao\":\"PIX\"}", CondicaoPagamentoRequestDTO.class).condicao())
                .isEqualTo(CondicaoPagamento.PIX);
        assertThat(mapper.readValue("{\"condicaoPagamento\":\"PARCELADO_21\"}", CondicaoPagamentoRequestDTO.class).condicao())
                .isEqualTo(CondicaoPagamento.PARCELADO_21);
    }
}
