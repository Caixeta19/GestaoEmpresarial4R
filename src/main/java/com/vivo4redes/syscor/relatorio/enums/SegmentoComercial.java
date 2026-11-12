package com.vivo4redes.syscor.relatorio.enums;

/**
 * Segmento do indicador Comercial do relatório de Serviços (docs/relatorios_produtos_servicos.md). Tabela única do
 * mapa segmento → (serviço normalizado, tipo de plano) já gravados em {@code itens_venda}; filtro e relatório usam
 * esta mesma definição. Só entram os segmentos que têm correspondência hoje: Alta Terminais, as Altas Fixa, Internet
 * Móvel, Migração Tecnológica, Outros Serviços e Upgrade não são distinguíveis pelos dados e ficam de fora.
 * <p>
 * Migração: o tipo de plano é o do plano de destino (a origem não é guardada); por isso "Migrações Pré-X" lista as
 * migrações cujo destino é X.
 * <p>
 * O predicado usa as colunas {@code servico_norm} (tipo_servico sem acento, minúsculo) e {@code tipo_plano} da
 * vw_relatorio_itens. Valores fixos no código: nunca vêm do cliente da API.
 */
public enum SegmentoComercial {

    ALTAS_CONTROLE("Altas Controle", "servico_norm LIKE 'alta%' AND tipo_plano = 'CONTROLE'"),
    ALTAS_PRE_PAGO("Altas Pré-Pago", "servico_norm LIKE 'alta%' AND tipo_plano = 'PRE'"),
    ALTAS_POS_PURO("Altas Pós-Puro", "servico_norm LIKE 'alta%' AND tipo_plano = 'POS'"),
    ALTAS_SVA("Altas SVA", "servico_norm = 'sva'"),
    MIGRACOES_PRE_CONTROLE("Migrações Pré-Controle", "servico_norm LIKE 'migracao%' AND tipo_plano = 'CONTROLE'"),
    MIGRACOES_PRE_PURO("Migrações Pré-Puro", "servico_norm LIKE 'migracao%' AND tipo_plano = 'PRE'"),
    MIGRACOES_PRE_POS_VIVO_TOTAL("Migrações Pré-Pós Vivo Total", "servico_norm LIKE 'migracao%' AND tipo_plano = 'POS'"),
    REATIVACAO_CONTROLE("Reativação Controle", "servico_norm LIKE 'reativacao%' AND tipo_plano = 'CONTROLE'"),
    REATIVACAO_POS("Reativação Pós", "servico_norm LIKE 'reativacao%' AND tipo_plano = 'POS'"),
    SEGURO_VIVO("Seguro Vivo", "servico_norm = 'seguro'"),
    TROCA_DE_SIMCARD("Troca de SimCard", "servico_norm = 'troca de simcard'"),
    TROCA_DE_NUMERO("Troca de número", "servico_norm = 'troca de numero'"),
    TROCA_DE_TITULARIDADE("Troca de titularidade", "servico_norm = 'troca de titularidade'");

    private final String rotulo;
    private final String predicadoSql;

    SegmentoComercial(String rotulo, String predicadoSql) {
        this.rotulo = rotulo;
        this.predicadoSql = predicadoSql;
    }

    public String rotulo() {
        return rotulo;
    }

    public String predicadoSql() {
        return predicadoSql;
    }
}
