package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.service.PrecoCalculado;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** GET /produtos/precos/item — preço de uma unidade do estoque para plano, tabela e pagamento. */
public record PrecoItemResponseDTO(
        String nomeComercial,
        TabelaPreco tabela,
        GrupoPlano grupoPlano,
        BigDecimal preco,
        BigDecimal precoPix,
        Map<Integer, BigDecimal> parcelas,
        BigDecimal valorAplicado,
        LocalDate vigencia,
        /** Desconto do fabricante já embutido no preço (só quando a tabela usada é RENOVA). */
        BigDecimal descontoFabricante,
        /** Todas as condições de pagamento, para o grupo do plano e a tabela aplicada. */
        List<OpcaoPagamentoDTO> opcoes
) {
    public static PrecoItemResponseDTO from(PrecoCalculado p) {
        return new PrecoItemResponseDTO(p.nomeComercial(), p.tabela(), p.grupoPlano(), p.preco(), p.precoPix(),
                p.parcelas(), p.valorAplicado(), p.vigencia(), p.descontoFabricante(), p.opcoes());
    }
}
