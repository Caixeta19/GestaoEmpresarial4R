package com.vivo4redes.syscor.relatorio.enums;

/** Opções fixas dos filtros dos relatórios analíticos. */
public final class OpcoesFiltro {

    private OpcoesFiltro() {
    }

    public enum Modo { ANALITICO, SINTETICO }

    /** Data usada pelo período: a da venda ou a da ativação (protocolo documental). */
    public enum Considerar { VENDA, ATIVACAO }

    /** Agrupamento do relatório de Produtos. */
    public enum AgruparPor { TIPO_PRODUTO, CATEGORIA }

    /** Status da venda no filtro; "Ambas" = concluídas e canceladas depois de lançadas. */
    public enum StatusVendaFiltro { CONCLUIDA, CANCELADA, AMBAS }
}
