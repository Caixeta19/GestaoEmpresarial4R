package com.vivo4redes.syscor.exception;

import com.vivo4redes.syscor.venda.dto.response.PagamentoIncompativelDTO;
import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PagamentosIncompativeisExceptionTest {

    @Test
    @DisplayName("Vira 422 com a lista de pagamentos a remover")
    void corpoDaResposta() {
        List<PagamentoIncompativelDTO> linhas = List.of(
                new PagamentoIncompativelDTO(7L, FormaPagamento.CARTAO_CREDITO, new BigDecimal("100.00"), 3));

        ResponseEntity<Map<String, Object>> resposta = new GlobalExceptionHandler().handlePagamentosIncompativeis(
                new PagamentosIncompativeisException(CondicaoPagamento.PIX, linhas));

        assertThat(resposta.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(resposta.getBody()).containsEntry("pagamentosIncompativeis", linhas);
        assertThat((String) resposta.getBody().get("mensagem")).contains("PIX", "CARTAO_CREDITO");
    }
}
