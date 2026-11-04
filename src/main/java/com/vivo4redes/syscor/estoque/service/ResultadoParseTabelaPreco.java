package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Conteúdo lido da planilha da Telefônica, antes de gravar.
 *
 * @param vigencia    data de "Vigência a partir de" (aba ÍNDICE); null se não encontrada
 * @param precos      um preço por (aba, tabela, grupo do plano, Nome Comercial)
 * @param planos      aba PLANOS
 * @param produtos    aba PRODUTOS
 * @param linhasPorAba aba -> quantidade de itens com preço lidos
 */
record ResultadoParseTabelaPreco(
        LocalDate vigencia,
        List<LinhaPreco> precos,
        List<LinhaPlano> planos,
        List<LinhaProduto> produtos,
        Map<String, Integer> linhasPorAba
) {

    /** Preço cheio (2x a 12x), PIX/Vivo Pay e total de 13x a 21x (só as parcelas presentes). */
    /** {@code descontoFabricante}: coluna DESCONTO do bloco TABELA RENOVA (null nas linhas REGULAR). */
    record LinhaPreco(String aba, TabelaPreco tabela, GrupoPlano grupo, String nomeComercial,
                      BigDecimal preco, BigDecimal precoPix, Map<Integer, BigDecimal> parcelas,
                      BigDecimal descontoFabricante) {
    }

    record LinhaPlano(String nomePlano, String sistema, String codSistema, String codSap,
                      GrupoPlano grupo, boolean disponivelAlta) {
    }

    record LinhaProduto(String codSap, String descricaoSap, String modeloDpgc, String cor,
                        String nomeComercial, String categoria) {
    }
}
