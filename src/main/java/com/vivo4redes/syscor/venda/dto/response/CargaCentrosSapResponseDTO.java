package com.vivo4redes.syscor.venda.dto.response;

import java.util.List;

/** Resultado da carga ADABAS;CENTRO_SAP[;DEPOSITO_SAP]: linhas lidas, filiais atualizadas e o que ficou de fora. */
public record CargaCentrosSapResponseDTO(
        int linhasLidas,
        int filiaisAtualizadas,
        List<String> codigosNaoEncontrados,
        List<String> erros
) {
}
