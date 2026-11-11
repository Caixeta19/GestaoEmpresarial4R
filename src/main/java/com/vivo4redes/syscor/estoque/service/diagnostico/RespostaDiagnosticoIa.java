package com.vivo4redes.syscor.estoque.service.diagnostico;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Formato exigido da resposta (structured outputs): um diagnóstico por IMEI e um resumo do lote. */
public record RespostaDiagnosticoIa(
        @JsonPropertyDescription("Um diagnóstico para cada IMEI recebido, sem inventar IMEIs.")
        List<Item> itens,
        @JsonPropertyDescription("Resumo do lote em português, de 3 a 5 frases, com as causas mais frequentes.")
        String resumo
) {

    public record Item(
            @JsonPropertyDescription("O IMEI/serial exatamente como recebido.")
            String imei,
            CausaProvavelIa causaProvavel,
            ConfiancaIa confianca,
            @JsonPropertyDescription("Ação sugerida para a loja, em português, em uma frase curta.")
            String acaoSugerida
    ) {
    }
}
