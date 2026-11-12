package com.vivo4redes.syscor.relatorio.enums;

import com.vivo4redes.syscor.exception.NegocioException;

import java.text.Normalizer;
import java.util.Locale;

/** Os três relatórios analíticos: de onde leem e que item entra (produto, serviço) ou a venda inteira. */
public enum TipoRelatorio {

    PRODUTOS("vw_relatorio_itens", "eh_produto", "venda_de_produtos"),
    SERVICOS("vw_relatorio_itens", "eh_servico", "venda_de_servicos"),
    VENDAS("vw_relatorio_vendas", null, "vendas");

    private final String view;
    private final String condicaoItem;
    private final String nomeArquivo;

    TipoRelatorio(String view, String condicaoItem, String nomeArquivo) {
        this.view = view;
        this.condicaoItem = condicaoItem;
        this.nomeArquivo = nomeArquivo;
    }

    public String view() {
        return view;
    }

    /** Coluna booleana da view que escolhe as linhas deste relatório; nula em Vendas (uma linha por venda). */
    public String condicaoItem() {
        return condicaoItem;
    }

    public String nomeArquivo() {
        return nomeArquivo;
    }

    /** Aceita o texto da URL: "produtos", "servicos", "serviços", "vendas". */
    public static TipoRelatorio de(String texto) {
        String t = texto == null ? "" : Normalizer.normalize(texto.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        for (TipoRelatorio tipo : values()) {
            if (tipo.name().equals(t)) return tipo;
        }
        throw new NegocioException("Relatório desconhecido: '" + texto + "'. Use produtos, servicos ou vendas.");
    }
}
