package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.enums.OrigemSku;
import com.vivo4redes.syscor.estoque.enums.ResultadoSku;
import com.vivo4redes.syscor.estoque.model.InventarioSku;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static com.vivo4redes.syscor.estoque.enums.ResultadoSku.*;

/**
 * Classificação de uma linha da conferência por SKU. Tabela única, em ordem de prioridade (a primeira que vale ganha):
 * <ol>
 *   <li>{@code SKU_NAO_IDENTIFICADO}: linha agrupada dos itens sem SKU;</li>
 *   <li>{@code SO_NO_SAP}: MB52 livre &gt; 0 e nada no sistema;</li>
 *   <li>{@code SO_NO_SISTEMA}: sem MB52 livre e com estoque no sistema;</li>
 *   <li>{@code CONTAGEM_DIVERGENTE}: já contou e a contagem difere da MB52 (referência = SAP);</li>
 *   <li>{@code SAP_ACIMA_SISTEMA} / {@code SAP_ABAIXO_SISTEMA}: MB52 ≠ sistema;</li>
 *   <li>{@code MB52_DIFERE_IQ09}: MB52 ≠ IQ09 (não vale para acessório, que não é serializado no SAP);</li>
 *   <li>{@code CONFERIDO}.</li>
 * </ol>
 * Quantidade principal = utilização livre; qualidade, bloqueado e trânsito só aparecem como informação.
 */
public final class InventarioSkuClassificador {

    private InventarioSkuClassificador() {
    }

    public static void classificar(InventarioSku sku) {
        BigDecimal mb52 = sku.getQtdMb52Livre();
        BigDecimal sistema = BigDecimal.valueOf(sku.getQtdSistema());
        BigDecimal iq09 = BigDecimal.valueOf(sku.getQtdIq09());
        BigDecimal contagem = sku.contou() ? sku.contagemEfetiva() : null;

        sku.setDifSapSistema(mb52.subtract(sistema));
        sku.setDifSapIq09(mb52.subtract(iq09));
        sku.setDifContagem(contagem == null ? null : contagem.subtract(mb52));
        sku.setValorDiferenca(sku.getValorUnitarioSap() == null ? null
                : sku.getValorUnitarioSap().multiply(sku.getDifSapSistema()).setScale(2, RoundingMode.HALF_UP));

        boolean temMb52 = mb52.signum() > 0;
        boolean temSistema = sku.getQtdSistema() > 0;
        boolean acessorio = sku.getOrigemSku() == OrigemSku.ACESSORIO;

        if (sku.naoIdentificado()) {
            definir(sku, SKU_NAO_IDENTIFICADO, sku.getQtdSistema() + " no sistema e " + sku.getQtdBipada()
                    + " bipado(s) sem SKU identificado (sem material na IQ09 nem na tabela de preços).");
        } else if (temMb52 && !temSistema) {
            definir(sku, SO_NO_SAP, "MB52 com " + fmt(mb52) + " livre(s) e nada no estoque do sistema da filial.");
        } else if (!temMb52 && temSistema) {
            definir(sku, SO_NO_SISTEMA, sku.isTemLinhaMb52()
                    ? "MB52 sem utilização livre e " + sku.getQtdSistema() + " no sistema."
                    : "Sem linha na MB52 da filial e " + sku.getQtdSistema() + " no sistema.");
        } else if (contagem != null && contagem.compareTo(mb52) != 0) {
            definir(sku, CONTAGEM_DIVERGENTE, "Contagem " + fmt(contagem) + " x MB52 " + fmt(mb52)
                    + " (sistema " + sku.getQtdSistema() + ").");
        } else if (mb52.compareTo(sistema) > 0) {
            definir(sku, SAP_ACIMA_SISTEMA, "MB52 " + fmt(mb52) + " x sistema " + sku.getQtdSistema() + explicacaoTransito(sku));
        } else if (mb52.compareTo(sistema) < 0) {
            definir(sku, SAP_ABAIXO_SISTEMA, "MB52 " + fmt(mb52) + " x sistema " + sku.getQtdSistema() + explicacaoTransito(sku));
        } else if (!acessorio && mb52.compareTo(iq09) != 0) {
            definir(sku, MB52_DIFERE_IQ09, "MB52 " + fmt(mb52) + " x IQ09 " + sku.getQtdIq09()
                    + " (serial fora da IQ09, item sem série ou IQ09 desatualizada).");
        } else {
            definir(sku, CONFERIDO, "MB52, IQ09 e sistema batem" + (contagem == null ? "." : " com a contagem."));
        }
    }

    /** Diferença SAP x sistema igual ao estoque em trânsito: provável transferência ainda não recebida. */
    private static String explicacaoTransito(InventarioSku sku) {
        BigDecimal transito = sku.getQtdMb52Transito();
        if (transito.signum() != 0
                && sku.getQtdMb52Livre().add(transito).compareTo(BigDecimal.valueOf(sku.getQtdSistema())) == 0) {
            return "; diferença explicada pelo estoque em trânsito (" + fmt(transito) + ").";
        }
        return ".";
    }

    private static void definir(InventarioSku sku, ResultadoSku resultado, String observacao) {
        sku.setResultado(resultado);
        String texto = observacao;
        if (sku.getUnidadesEstimadas() > 0) {
            texto += " " + sku.getUnidadesEstimadas() + " unidade(s) com SKU estimado pela tabela de preços.";
        }
        sku.setObservacao(texto.length() > 300 ? texto.substring(0, 300) : texto);
    }

    static String fmt(BigDecimal valor) {
        return valor.stripTrailingZeros().toPlainString().replace('.', ',');
    }
}
