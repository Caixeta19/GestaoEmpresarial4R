package com.vivo4redes.syscor.venda.repository;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.model.PagamentoVenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PagamentoVendaRepository extends JpaRepository<PagamentoVenda, Long> {

    List<PagamentoVenda> findByVendaIdOrderByIdAsc(Long vendaId);

    /** Outras vendas não canceladas com esse voucher em linha de pagamento (histórico anterior à V28). */
    @Query("""
        select v.id from PagamentoVenda p
        join p.venda v
        where p.forma = com.vivo4redes.syscor.venda.enums.FormaPagamento.VOUCHER
          and p.codigoVoucher = :codigo
          and v.status <> com.vivo4redes.syscor.venda.enums.StatusVenda.CANCELADA
          and v.id <> :vendaId
        order by v.id
        """)
    List<Long> buscarOutrasVendasComVoucher(@Param("codigo") String codigo, @Param("vendaId") Long vendaId);

    /** Entradas do caixa: pagamentos das vendas CONCLUIDAS lançadas nele, por forma (venda cancelada sai da soma). */
    @Query("""
        select p.forma, coalesce(sum(p.valor), 0)
        from PagamentoVenda p
        join p.venda v
        where v.caixaSessao.id = :caixaId
          and v.status = com.vivo4redes.syscor.venda.enums.StatusVenda.CONCLUIDA
        group by p.forma
        """)
    List<Object[]> somarPorCaixaEForma(@Param("caixaId") Long caixaId);

    /** Uma linha por pagamento lançado no caixa, de qualquer status de venda (movimento detalhado). */
    @Query("""
        select p from PagamentoVenda p
        join fetch p.venda v
        join fetch v.usuario
        where v.caixaSessao.id = :caixaId
        order by coalesce(v.finalizadoEm, p.criadoEm), p.id
        """)
    List<PagamentoVenda> listarDoCaixa(@Param("caixaId") Long caixaId);
}
