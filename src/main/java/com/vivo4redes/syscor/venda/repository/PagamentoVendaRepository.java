package com.vivo4redes.syscor.venda.repository;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import com.vivo4redes.syscor.venda.model.PagamentoVenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
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

    @Query("""
        SELECT COALESCE(SUM(p.valor), 0)
        FROM PagamentoVenda p
        JOIN p.venda v
        WHERE v.filial.id = :filialId
          AND p.forma = :forma
          AND v.criadoEm >= :inicio
          AND v.criadoEm < :fim
    """)
    BigDecimal somarPorFilialFormaEData(
            @Param("filialId") Long filialId,
            @Param("forma") FormaPagamento forma,
            @Param("inicio") Instant inicio,
            @Param("fim") Instant fim
    );
}