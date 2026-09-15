package com.vivo4redes.syscor.estoque.service;
import com.vivo4redes.syscor.estoque.dto.LinhaPrecoDTO;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @param indiceSkuPorNomeComercial Nome Comercial normalizado -> Cod_Sap(s), extraído da aba PRODUTOS.
 *                                  Um Nome Comercial pode apontar para mais de um Cod_Sap (variações de cor).
 */
record ResultadoParseTabelaPreco(
        Map<String, Set<String>> indiceSkuPorNomeComercial,
        List<LinhaPrecoDTO> linhasPreco,
        String vigenciaEm
) {
}