package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;
import com.vivo4redes.syscor.estoque.model.InventarioItem;

import java.util.Locale;
import java.util.Set;

import static com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario.*;

/**
 * Classificação de um serial do inventário a partir das três fontes (IQ09 da filial, estoque da filial e bipagem).
 * Determinística: o cruzamento já foi feito pelo serial normalizado; aqui só se decide o resultado.
 */
public final class InventarioClassificador {

    /** Status usuário da IQ09 que significam "no estoque da loja": ADEP (atribuído ao depósito) ou vazio. */
    private static final Set<String> STATUS_USUARIO_ESTOQUE_LOJA = Set.of("", "ADEP");

    private InventarioClassificador() {
    }

    /** A linha da IQ09 entra como esperada na loja? */
    public static boolean statusUsuarioDeEstoqueDaLoja(String statusUsuario) {
        return STATUS_USUARIO_ESTOQUE_LOJA.contains(statusUsuario == null ? "" : statusUsuario.trim().toUpperCase(Locale.ROOT));
    }

    /** Recalcula resultado e observação do item. {@code fechado}: pendentes viram FALTA (IQ09) ou SO_NO_SISTEMA. */
    public static void classificar(InventarioItem item, boolean fechado) {
        boolean bipado = item.bipado();
        boolean veioDaIq09 = item.getCentroSap() != null && !item.getCentroSap().isBlank();
        boolean iq09ForaDoEstoqueDaLoja = veioDaIq09 && !item.isEsperadoIq09();
        boolean vendidoNoSistema = "VENDIDO".equals(item.getStatusSistema());

        if (item.isEsperadoIq09() && vendidoNoSistema) {
            definir(item, VENDIDO_EM_ESTOQUE, "Na IQ09 da loja, mas VENDIDO no sistema"
                    + (bipado ? " e bipado na loja." : ". Conferir a venda e a baixa no SAP."));
            return;
        }
        if (iq09ForaDoEstoqueDaLoja && bipado) {
            definir(item, STATUS_SAP_DIVERGENTE, "Bipado na loja, mas na IQ09 o status usuário é "
                    + textoStatusUsuario(item) + ".");
            return;
        }
        if (item.isEsperadoIq09()) {
            if (bipado) {
                if (item.isEsperadoSistema()) {
                    definir(item, CONFERIDO, observacaoConferido(item));
                } else if (item.getFilialSistema() != null) {
                    definir(item, SEM_ENTRADA_SISTEMA, "Na IQ09 da loja, mas no sistema está em " + item.getFilialSistema()
                            + " (" + item.getStatusSistema() + ").");
                } else {
                    definir(item, SEM_ENTRADA_SISTEMA, "Na IQ09 da loja e bipado, sem entrada no estoque do sistema.");
                }
            } else {
                definir(item, fechado ? FALTA : PENDENTE, fechado
                        ? "Na IQ09 da loja e não localizado na contagem."
                        : "Na IQ09 da loja: falta bipar.");
            }
            return;
        }
        if (item.isEsperadoSistema()) {
            if (bipado) {
                definir(item, SOBRA_FISICA, iq09ForaDoEstoqueDaLoja
                        ? "Bipado e no estoque da loja, mas na IQ09 o status usuário é " + textoStatusUsuario(item) + "."
                        : "Bipado e no estoque da loja, mas fora da IQ09 da filial.");
            } else {
                definir(item, fechado ? SO_NO_SISTEMA : PENDENTE, fechado
                        ? "Só no estoque do sistema (fora da IQ09) e não localizado na contagem."
                        : "No estoque do sistema (fora da IQ09): falta bipar.");
            }
            return;
        }
        if (iq09ForaDoEstoqueDaLoja) {
            definir(item, FORA_ESTOQUE_SAP, "Na IQ09 com status usuário " + textoStatusUsuario(item)
                    + ": fora do estoque da loja.");
            return;
        }
        // Bipado e fora de tudo que era esperado.
        if (item.getFilialSistema() != null) {
            definir(item, SOBRA_FISICA, "Fora da IQ09 da filial; no sistema está em " + item.getFilialSistema()
                    + " (" + item.getStatusSistema() + ").");
        } else {
            definir(item, SOBRA_FISICA, "Fora da IQ09 da filial e sem cadastro no estoque do sistema.");
        }
    }

    private static String observacaoConferido(InventarioItem item) {
        if ("RESERVADO".equals(item.getStatusSistema())) {
            return "Conferido; no sistema está RESERVADO em um carrinho aberto.";
        }
        if ("DEFEITO".equals(item.getStatusSistema())) {
            return "Conferido; no sistema está com DEFEITO.";
        }
        return "Na IQ09, no sistema e bipado.";
    }

    private static String textoStatusUsuario(InventarioItem item) {
        String status = item.getStatusUsuarioSap();
        return status == null || status.isBlank() ? "(vazio)" : status;
    }

    private static void definir(InventarioItem item, ResultadoConciliacaoInventario resultado, String observacao) {
        item.setResultado(resultado);
        item.setObservacao(observacao.length() > 300 ? observacao.substring(0, 300) : observacao);
    }
}
