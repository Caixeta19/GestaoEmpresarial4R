package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.dto.response.OpcaoPagamentoDTO;
import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Preço de um item na tabela vigente para (plano, tabela, forma de pagamento).
 *
 * @param tabela        tabela efetivamente usada (REGULAR para itens sem plano, mesmo se pedida RENOVA)
 * @param grupoPlano    grupo efetivamente usado (SEM_PLANO para itens das abas sem plano)
 * @param valorAplicado valor do item para a forma de pagamento: PIX -> preço PIX; 13x–21x -> total da parcela;
 *                      demais -> preço cheio
 * @param descontoFabricante desconto do fabricante já embutido no preço Renova (null quando a tabela usada é REGULAR)
 * @param opcoes        todas as condições de pagamento do item, para o grupo e a tabela usados
 */
public record PrecoCalculado(
        Long execucaoId,
        LocalDate vigencia,
        String nomeComercial,
        TabelaPreco tabela,
        GrupoPlano grupoPlano,
        BigDecimal preco,
        BigDecimal precoPix,
        Map<Integer, BigDecimal> parcelas,
        FormaPagamento formaPagamento,
        Integer numeroParcelas,
        BigDecimal valorAplicado,
        BigDecimal descontoFabricante,
        List<OpcaoPagamentoDTO> opcoes
) {
}
