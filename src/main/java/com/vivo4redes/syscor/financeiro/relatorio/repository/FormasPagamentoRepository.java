package com.vivo4redes.syscor.financeiro.relatorio.repository;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.model.PagamentoVenda;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Consultas agregadas do relatório de formas de pagamento. Mesma regra de {@code somarPorCaixaEForma}: só vendas
 * CONCLUIDA, sem filtrar o tipo da linha (ENTRADA e FINANCIADO entram). O período é {@code [inicio, fim)} sobre
 * {@code finalizadoEm}. {@code todasFiliais = true} ignora a lista de filiais (que vai com um valor qualquer, porque
 * lista vazia não vale em IN).
 */
public interface FormasPagamentoRepository extends Repository<PagamentoVenda, Long> {

    /** filial id, filial nome, caixa (null = venda sem caixa), forma, soma, pagamentos. */
    @Query("""
        select f.id, f.nome, cs.caixaPdv, p.forma, coalesce(sum(p.valor), 0), count(p)
        from PagamentoVenda p
        join p.venda v
        join v.filial f
        left join v.caixaSessao cs
        where v.status = com.vivo4redes.syscor.venda.enums.StatusVenda.CONCLUIDA
          and v.finalizadoEm >= :inicio and v.finalizadoEm < :fim
          and (:todasFiliais = true or f.id in :filialIds)
          and p.forma in :formas
        group by f.id, f.nome, cs.caixaPdv, p.forma
        """)
    List<Object[]> agregarPorFilialCaixaForma(@Param("inicio") Instant inicio, @Param("fim") Instant fim,
                                              @Param("todasFiliais") boolean todasFiliais,
                                              @Param("filialIds") Collection<Long> filialIds,
                                              @Param("formas") Collection<FormaPagamento> formas);

    /** filial id, caixa, vendas distintas (uma venda pertence a um único caixa). */
    @Query("""
        select f.id, cs.caixaPdv, count(distinct v.id)
        from PagamentoVenda p
        join p.venda v
        join v.filial f
        left join v.caixaSessao cs
        where v.status = com.vivo4redes.syscor.venda.enums.StatusVenda.CONCLUIDA
          and v.finalizadoEm >= :inicio and v.finalizadoEm < :fim
          and (:todasFiliais = true or f.id in :filialIds)
          and p.forma in :formas
        group by f.id, cs.caixaPdv
        """)
    List<Object[]> vendasPorFilialCaixa(@Param("inicio") Instant inicio, @Param("fim") Instant fim,
                                        @Param("todasFiliais") boolean todasFiliais,
                                        @Param("filialIds") Collection<Long> filialIds,
                                        @Param("formas") Collection<FormaPagamento> formas);

    /** forma, vendas distintas com essa forma. */
    @Query("""
        select p.forma, count(distinct v.id)
        from PagamentoVenda p
        join p.venda v
        where v.status = com.vivo4redes.syscor.venda.enums.StatusVenda.CONCLUIDA
          and v.finalizadoEm >= :inicio and v.finalizadoEm < :fim
          and (:todasFiliais = true or v.filial.id in :filialIds)
          and p.forma in :formas
        group by p.forma
        """)
    List<Object[]> vendasPorForma(@Param("inicio") Instant inicio, @Param("fim") Instant fim,
                                  @Param("todasFiliais") boolean todasFiliais,
                                  @Param("filialIds") Collection<Long> filialIds,
                                  @Param("formas") Collection<FormaPagamento> formas);

    @Query("""
        select count(distinct v.id)
        from PagamentoVenda p
        join p.venda v
        where v.status = com.vivo4redes.syscor.venda.enums.StatusVenda.CONCLUIDA
          and v.finalizadoEm >= :inicio and v.finalizadoEm < :fim
          and (:todasFiliais = true or v.filial.id in :filialIds)
          and p.forma in :formas
        """)
    long contarVendas(@Param("inicio") Instant inicio, @Param("fim") Instant fim,
                      @Param("todasFiliais") boolean todasFiliais, @Param("filialIds") Collection<Long> filialIds,
                      @Param("formas") Collection<FormaPagamento> formas);

    /** Vendas CONCLUIDA sem finalizadoEm no escopo de filiais (ficam fora do relatório). */
    @Query("""
        select count(v)
        from Venda v
        where v.status = com.vivo4redes.syscor.venda.enums.StatusVenda.CONCLUIDA
          and v.finalizadoEm is null
          and (:todasFiliais = true or v.filial.id in :filialIds)
        """)
    long contarSemDataFinalizacao(@Param("todasFiliais") boolean todasFiliais,
                                  @Param("filialIds") Collection<Long> filialIds);

    /** finalizadoEm, nº da venda, filial, caixa, forma, tipo, valor, parcelas; ordenado, para o detalhe da exportação. */
    @Query("""
        select v.finalizadoEm, v.numeroVenda, f.nome, cs.caixaPdv, p.forma, p.tipo, p.valor, p.parcelas
        from PagamentoVenda p
        join p.venda v
        join v.filial f
        left join v.caixaSessao cs
        where v.status = com.vivo4redes.syscor.venda.enums.StatusVenda.CONCLUIDA
          and v.finalizadoEm >= :inicio and v.finalizadoEm < :fim
          and (:todasFiliais = true or f.id in :filialIds)
          and p.forma in :formas
        order by v.finalizadoEm, v.id, p.id
        """)
    List<Object[]> detalhe(@Param("inicio") Instant inicio, @Param("fim") Instant fim,
                           @Param("todasFiliais") boolean todasFiliais,
                           @Param("filialIds") Collection<Long> filialIds,
                           @Param("formas") Collection<FormaPagamento> formas, Pageable limite);
}
