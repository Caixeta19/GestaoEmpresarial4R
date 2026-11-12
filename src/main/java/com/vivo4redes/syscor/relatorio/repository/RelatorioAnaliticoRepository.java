package com.vivo4redes.syscor.relatorio.repository;

import com.vivo4redes.syscor.relatorio.dto.CampoRelatorio;
import com.vivo4redes.syscor.relatorio.dto.FiltroRelatorioDTO;
import com.vivo4redes.syscor.relatorio.enums.OpcoesFiltro.AgruparPor;
import com.vivo4redes.syscor.relatorio.enums.OpcoesFiltro.Considerar;
import com.vivo4redes.syscor.relatorio.enums.OpcoesFiltro.StatusVendaFiltro;
import com.vivo4redes.syscor.relatorio.enums.SegmentoComercial;
import com.vivo4redes.syscor.relatorio.enums.TipoRelatorio;
import com.vivo4redes.syscor.util.TextoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Leitura das views vw_relatorio_itens e vw_relatorio_vendas (V37). O SQL nasce só de nomes de coluna fixos no
 * catálogo; todo valor do usuário vai como parâmetro nomeado.
 */
@Repository
@RequiredArgsConstructor
public class RelatorioAnaliticoRepository {

    /** Grupos do relatório de Produtos. */
    static final String GRUPO_TIPO_PRODUTO = "coalesce(nullif(trim(tipo_produto), ''), 'Sem tipo de produto')";
    static final String GRUPO_CATEGORIA = "coalesce(nullif(trim(categoria_estoque), ''), 'Sem categoria')";

    /** Colunas extras que a leitura de Produtos traz depois dos campos: grupo, quantidade, venda, custo e lucro. */
    public static final int EXTRAS_PRODUTOS = 5;

    private static final String SEM_ACENTO =
            "translate(lower(trim(%s)), 'áàâãäéèêëíìîïóòôõöúùûüç', 'aaaaaeeeeiiiiooooouuuuc')";

    private final NamedParameterJdbcTemplate jdbc;

    private record Filtro(String where, MapSqlParameterSource params) {
    }

    public static String grupo(FiltroRelatorioDTO f) {
        return f.agruparPor() == AgruparPor.CATEGORIA ? GRUPO_CATEGORIA : GRUPO_TIPO_PRODUTO;
    }

    public long contar(TipoRelatorio tipo, FiltroRelatorioDTO f, boolean sintetico) {
        Filtro w = filtrar(tipo, f);
        String base = "SELECT 1 FROM " + tipo.view() + " WHERE " + w.where();
        String sql = sintetico
                ? "SELECT count(*) FROM (SELECT 1 FROM " + tipo.view() + " WHERE " + w.where() + " GROUP BY "
                        + chavesSinteticas(tipo, f) + ") g"
                : "SELECT count(*) FROM (" + base + ") t";
        Long n = jdbc.queryForObject(sql, w.params(), Long.class);
        return n == null ? 0 : n;
    }

    /**
     * Linhas analíticas com os campos escolhidos (nessa ordem). Em Produtos, vêm mais {@link #EXTRAS_PRODUTOS}
     * valores no fim (grupo, quantidade, valor de venda, custo, lucro) para os subtotais. {@code limite} nulo = todas.
     */
    public void analitico(TipoRelatorio tipo, FiltroRelatorioDTO f, List<CampoRelatorio> campos, Integer limite,
                          long deslocamento, Consumer<Object[]> linha) {
        Filtro w = filtrar(tipo, f);
        List<String> colunas = campos.stream().map(c -> c.coluna() + " AS \"" + c.chave() + "\"").collect(Collectors.toList());
        if (tipo == TipoRelatorio.PRODUTOS) {
            colunas.add(grupo(f) + " AS \"_grupo\"");
            colunas.add("quantidade AS \"_quantidade\"");
            colunas.add("valor_venda AS \"_venda\"");
            colunas.add("valor_custo AS \"_custo\"");
            colunas.add("valor_lucro AS \"_lucro\"");
        }
        String ordem = tipo == TipoRelatorio.VENDAS
                ? "filial, data_venda, numero_venda"
                : (tipo == TipoRelatorio.PRODUTOS ? "filial, " + grupo(f) + ", data_venda, numero_venda, item_id"
                        : "filial, data_venda, numero_venda, item_id");
        String sql = "SELECT " + String.join(", ", colunas) + " FROM " + tipo.view() + " WHERE " + w.where()
                + " ORDER BY " + ordem + (limite == null ? "" : " LIMIT " + limite + " OFFSET " + deslocamento);
        int n = colunas.size();
        jdbc.query(sql, w.params(), (ResultSet rs) -> {
            linha.accept(valores(rs, n));
        });
    }

    /**
     * Resumo sintético: totais por filial e vendedor (e por grupo em Produtos). Chaves do mapa: filial, vendedor,
     * grupo (Produtos), registros, quantidade (Produtos), valor, custo e lucro (Produtos).
     */
    public List<Map<String, Object>> sintetico(TipoRelatorio tipo, FiltroRelatorioDTO f, Integer limite, long deslocamento) {
        Filtro w = filtrar(tipo, f);
        String selecao = switch (tipo) {
            case PRODUTOS -> grupo(f) + " AS grupo, count(*) AS registros, sum(quantidade) AS quantidade, "
                    + "sum(valor_venda) AS valor, sum(valor_custo) AS custo, sum(valor_lucro) AS lucro";
            case SERVICOS -> "count(*) AS registros, sum(valor_receita) AS valor";
            case VENDAS -> "count(*) AS registros, sum(valor_total) AS valor";
        };
        String sql = "SELECT filial, vendedor, " + selecao + " FROM " + tipo.view() + " WHERE " + w.where()
                + " GROUP BY " + chavesSinteticas(tipo, f) + " ORDER BY " + chavesSinteticas(tipo, f)
                + (limite == null ? "" : " LIMIT " + limite + " OFFSET " + deslocamento);
        List<Map<String, Object>> saida = new ArrayList<>();
        jdbc.query(sql, w.params(), (ResultSet rs) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            var meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) m.put(meta.getColumnLabel(i), rs.getObject(i));
            saida.add(m);
        });
        return saida;
    }

    /** Subtotais por grupo de todo o filtro (Produtos): grupo, registros, quantidade, valor, custo, lucro. */
    public List<Map<String, Object>> subtotaisPorGrupo(FiltroRelatorioDTO f) {
        Filtro w = filtrar(TipoRelatorio.PRODUTOS, f);
        String sql = "SELECT " + grupo(f) + " AS grupo, count(*) AS registros, sum(quantidade) AS quantidade, "
                + "sum(valor_venda) AS valor, sum(valor_custo) AS custo, sum(valor_lucro) AS lucro FROM "
                + TipoRelatorio.PRODUTOS.view() + " WHERE " + w.where() + " GROUP BY " + grupo(f) + " ORDER BY 1";
        List<Map<String, Object>> saida = new ArrayList<>();
        jdbc.query(sql, w.params(), (ResultSet rs) -> {
            Map<String, Object> m = new LinkedHashMap<>();
            var meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) m.put(meta.getColumnLabel(i), rs.getObject(i));
            saida.add(m);
        });
        return saida;
    }

    private static String chavesSinteticas(TipoRelatorio tipo, FiltroRelatorioDTO f) {
        return tipo == TipoRelatorio.PRODUTOS ? "filial, vendedor, " + grupo(f) : "filial, vendedor";
    }

    private Filtro filtrar(TipoRelatorio tipo, FiltroRelatorioDTO f) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        List<String> where = new ArrayList<>();
        if (tipo.condicaoItem() != null) where.add(tipo.condicaoItem());

        StatusVendaFiltro status = f.statusVenda() != null ? f.statusVenda()
                : (tipo == TipoRelatorio.VENDAS ? StatusVendaFiltro.AMBAS : StatusVendaFiltro.CONCLUIDA);
        switch (status) {
            case CONCLUIDA -> where.add("status_venda = 'CONCLUIDA'");
            case CANCELADA -> where.add("status_venda = 'CANCELADA'");
            case AMBAS -> where.add("status_venda IN ('CONCLUIDA', 'CANCELADA')");
        }

        // Produto não tem ativação: "Considerar Data de Ativação" só vale para Serviços e Vendas
        boolean porAtivacao = f.considerar() == Considerar.ATIVACAO && tipo != TipoRelatorio.PRODUTOS;
        if (porAtivacao) {
            if (f.dataInicio() != null) {
                p.addValue("inicio", f.dataInicio());
                where.add("data_ativacao >= :inicio");
            }
            if (f.dataFim() != null) {
                p.addValue("fim", f.dataFim());
                where.add("data_ativacao <= :fim");
            }
        } else {
            // data_venda já vem na hora de São Paulo: o dia começa à meia-noite local
            if (f.dataInicio() != null) {
                p.addValue("inicio", f.dataInicio().atStartOfDay());
                where.add("data_venda >= :inicio");
            }
            if (f.dataFim() != null) {
                p.addValue("fim", f.dataFim().plusDays(1).atStartOfDay());
                where.add("data_venda < :fim");
            }
        }
        if (temItens(f.filialIds())) {
            p.addValue("filialIds", f.filialIds());
            where.add("filial_id IN (:filialIds)");
        }
        if (f.vendedorId() != null) {
            p.addValue("vendedorId", f.vendedorId());
            where.add("vendedor_id = :vendedorId");
        }
        if (f.clienteId() != null) {
            p.addValue("clienteId", f.clienteId());
            where.add("cliente_id = :clienteId");
        }
        if (f.q() != null && !f.q().isBlank()) {
            p.addValue("q", "%" + TextoUtil.normalizar(f.q()) + "%");
            where.add(SEM_ACENTO.formatted("cliente") + " LIKE :q");
        }
        if (f.statusBko() != null) {
            p.addValue("statusBko", f.statusBko().name());
            where.add("status_bko_cod = :statusBko");
        }
        if (tipo == TipoRelatorio.SERVICOS && temItens(f.segmentos())) {
            where.add(f.segmentos().stream().map(SegmentoComercial::predicadoSql)
                    .collect(Collectors.joining(") OR (", "((", "))")));
        }
        if (tipo == TipoRelatorio.PRODUTOS && temItens(f.tiposProduto())) {
            p.addValue("tiposProduto", f.tiposProduto().stream().map(Enum::name).toList());
            where.add("tipo_produto_filtro IN (:tiposProduto)");
        }
        return new Filtro(String.join(" AND ", where), p);
    }

    private static Object[] valores(ResultSet rs, int n) throws SQLException {
        Object[] v = new Object[n];
        for (int i = 0; i < n; i++) {
            Object o = rs.getObject(i + 1);
            if (o instanceof java.sql.Timestamp t) o = t.toLocalDateTime();
            else if (o instanceof java.sql.Date d) o = d.toLocalDate();
            else if (o instanceof java.sql.Time t) o = t.toLocalTime();
            v[i] = o;
        }
        return v;
    }

    private static boolean temItens(List<?> lista) {
        return lista != null && !lista.isEmpty();
    }
}
