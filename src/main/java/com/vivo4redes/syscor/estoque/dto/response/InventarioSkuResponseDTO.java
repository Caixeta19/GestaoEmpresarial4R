package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.enums.OrigemSku;
import com.vivo4redes.syscor.estoque.enums.ResultadoSku;
import com.vivo4redes.syscor.estoque.model.InventarioSku;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Uma linha da conferência por SKU. {@code sku} nulo = linha agrupada dos itens sem SKU identificado.
 * {@code valorDiferencaEstimado} é estimativa (valor unitário da MB52 × diferença SAP − sistema).
 */
public record InventarioSkuResponseDTO(
        Long id,
        String sku,
        String materialSap,
        String denominacao,
        String unidade,
        boolean temLinhaMb52,
        BigDecimal qtdMb52Livre,
        BigDecimal qtdMb52Qualidade,
        BigDecimal qtdMb52Bloqueado,
        BigDecimal qtdMb52Transito,
        BigDecimal outrosEstoquesSap,
        int qtdIq09,
        int qtdSistema,
        int qtdBipada,
        BigDecimal qtdContagemManual,
        BigDecimal contagem,
        String contagemJustificativa,
        String contadoPor,
        Instant contadoEm,
        OrigemSku origemSku,
        BigDecimal diferencaSapSistema,
        BigDecimal diferencaSapIq09,
        BigDecimal diferencaContagem,
        BigDecimal valorUnitarioSap,
        BigDecimal valorDiferencaEstimado,
        ResultadoSku resultado,
        String observacao,
        boolean permiteContagemManual
) {

    public static InventarioSkuResponseDTO from(InventarioSku s) {
        return new InventarioSkuResponseDTO(
                s.getId(),
                s.getMaterialNormalizado(),
                s.getMaterialSap(),
                s.getDenominacao(),
                s.getUnidade(),
                s.isTemLinhaMb52(),
                s.getQtdMb52Livre(),
                s.getQtdMb52Qualidade(),
                s.getQtdMb52Bloqueado(),
                s.getQtdMb52Transito(),
                s.getQtdMb52Qualidade().add(s.getQtdMb52Bloqueado()).add(s.getQtdMb52Transito()),
                s.getQtdIq09(),
                s.getQtdSistema(),
                s.getQtdBipada(),
                s.getQtdContagemManual(),
                s.contou() ? s.contagemEfetiva() : null,
                s.getContagemJustificativa(),
                s.getContadoPor() == null ? null : s.getContadoPor().getNome(),
                s.getContadoEm(),
                s.getOrigemSku(),
                s.getDifSapSistema(),
                s.getDifSapIq09(),
                s.getDifContagem(),
                s.getValorUnitarioSap(),
                s.getValorDiferenca(),
                s.getResultado(),
                s.getObservacao(),
                !s.naoIdentificado());
    }
}
