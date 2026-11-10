package com.vivo4redes.syscor.gestao.repository;

import com.vivo4redes.syscor.gestao.dto.request.FiltroDocumentalDTO;
import com.vivo4redes.syscor.gestao.enums.TipoPlano;
import com.vivo4redes.syscor.util.TextoUtil;
import com.vivo4redes.syscor.venda.enums.StatusVenda;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Leitura da vw_gestao_documental (V33) com os mesmos filtros da busca. Uma linha por item de serviço: os filtros de
 * item (serviço, tipo de plano, nº de acesso) escolhem as linhas, e não a venda inteira.
 */
@Repository
@RequiredArgsConstructor
public class RelatorioDocumentalRepository {

    /** As 45 colunas do relatório, com os nomes e a ordem da chview_gestao_documental_mt_4redes. */
    public static final List<String> COLUNAS = List.of(
            "ID do Serviço", "ID da Venda", "Serviço", "Plano", "Área de loja", "REDE_ID", "REDE", "PDV_ID", "PDV",
            "Adabas", "Cliente", "Tipo Cliente", "CPF/CNPJ do cliente", "Vendedor", "Status Vendedor", "Data da Venda",
            "DATA_SYS", "Ativação da Primeira Chamada", "Nº de Acesso", "Data de Ativação", "Data de Baixa",
            "Zerar Remuneração", "Data de Digitalização", "Nº Protocolo GED", "Nº Protocolo GED (Portabilidade)",
            "Status BKO", "Observação", "Observação Importação", "Situação do Serviço", "Motivo do Cancelamento",
            "Gerar comissão de serviço do vendedor", "Valor Remuneração", "status_primeira_fatura",
            "status_terceira_fatura", "status_segunda_fatura", "data_primeira_fatura", "data_segunda_fatura",
            "data_terceira_fatura", "valor_receita", "valor_considerado_tfp", "valor_penalizado_tfp",
            "Usuário Cancelamento", "Usuário Inserção", "Usuário Alteração", "Data Instalação");

    // translate(): tira os acentos no banco para comparar como TextoUtil.normalizar
    private static final String SEM_ACENTO =
            "translate(lower(trim(%s)), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc')";

    private final NamedParameterJdbcTemplate jdbc;

    /** Entrega cada linha (valores na ordem de {@link #COLUNAS}), em ordem de lançamento, venda e serviço. */
    public void percorrer(FiltroDocumentalDTO f, Consumer<Object[]> linha) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        List<String> where = new ArrayList<>();

        p.addValue("status", (f.incluirCanceladas()
                ? List.of(StatusVenda.CONCLUIDA, StatusVenda.CANCELADA)
                : List.of(StatusVenda.CONCLUIDA)).stream().map(Enum::name).toList());
        where.add("status_venda IN (:status)");
        // data_lancamento já vem da view na hora de São Paulo: o dia começa à meia-noite local, como na busca
        if (f.dataInicio() != null) {
            p.addValue("inicio", f.dataInicio().atStartOfDay());
            where.add("data_lancamento >= :inicio");
        }
        if (f.dataFim() != null) {
            p.addValue("fim", f.dataFim().plusDays(1).atStartOfDay());
            where.add("data_lancamento < :fim");
        }
        if (temItens(f.filialIds())) {
            p.addValue("filialIds", f.filialIds());
            where.add("filial_id IN (:filialIds)");
        }
        if (f.vendedorId() != null) {
            p.addValue("vendedorId", f.vendedorId());
            where.add("vendedor_id = :vendedorId");
        }
        if (f.numeroVenda() != null) {
            p.addValue("numeroVenda", f.numeroVenda());
            where.add("\"ID da Venda\" = :numeroVenda");
        }
        if (f.statusBko() != null) {
            p.addValue("statusBko", f.statusBko().name());
            where.add("status_bko = :statusBko");
        }
        String digitos = f.numeroAcesso() == null ? "" : f.numeroAcesso().replaceAll("\\D", "");
        if (!digitos.isEmpty()) {
            p.addValue("acesso", digitos);
            where.add("regexp_replace(\"Nº de Acesso\", '[^0-9]', '', 'g') = :acesso");
        }
        if (temItens(f.servicos())) {
            p.addValue("servicos", f.servicos().stream().map(TextoUtil::normalizar).toList());
            where.add(SEM_ACENTO.formatted("\"Serviço\"") + " IN (:servicos)");
        }
        if (temItens(f.tiposPlano())) {
            p.addValue("tiposPlano", f.tiposPlano().stream().map(TipoPlano::name).toList());
            where.add("tipo_plano IN (:tiposPlano)");
        }
        if (f.numeroProtocoloGed() != null && !f.numeroProtocoloGed().isBlank()) {
            p.addValue("ged", f.numeroProtocoloGed().trim().toUpperCase());
            where.add("upper(trim(\"Nº Protocolo GED\")) = :ged");
        }
        if (f.liderEquipe() != null && !f.liderEquipe().isBlank()) {
            p.addValue("lider", "%" + TextoUtil.normalizar(f.liderEquipe()) + "%");
            where.add(SEM_ACENTO.formatted("lider_equipe") + " LIKE :lider");
        }

        String sql = "SELECT " + COLUNAS.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(", "))
                + " FROM vw_gestao_documental WHERE " + String.join(" AND ", where)
                + " ORDER BY data_lancamento, \"ID da Venda\", \"ID do Serviço\"";
        jdbc.query(sql, p, (ResultSet rs) -> linha.accept(valores(rs)));
    }

    private static Object[] valores(ResultSet rs) throws SQLException {
        Object[] v = new Object[COLUNAS.size()];
        for (int i = 0; i < v.length; i++) {
            Object o = rs.getObject(i + 1);
            // DATE e TIMESTAMP sem fuso: os campos locais, sem conversão
            if (o instanceof java.sql.Timestamp t) o = t.toLocalDateTime();
            else if (o instanceof java.sql.Date d) o = d.toLocalDate();
            v[i] = o;
        }
        return v;
    }

    private static boolean temItens(List<?> lista) {
        return lista != null && !lista.isEmpty();
    }
}
