package com.vivo4redes.syscor.venda.repository;

import com.vivo4redes.syscor.venda.enums.StatusVenda;
import com.vivo4redes.syscor.venda.model.Venda;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Como spring.jpa.open-in-view=false (correto — evita sessão aberta pela
 * request toda), o DTO de resposta é montado no controller DEPOIS que a
 * transação do service já fechou. Isso quebra em qualquer relação LAZY
 * (cliente, filial, usuario, itens) com LazyInitializationException.
 * Solução: buscar a Venda já com tudo carregado, dentro da transação,
 * via @EntityGraph — sem precisar tornar as relações EAGER na entidade
 * (o que causaria N+1 em toda consulta que não precisa desses dados).
 */
public interface VendaRepository extends JpaRepository<Venda, Long> {

    List<Venda> findByClienteId(Long clienteId);

    List<Venda> findByStatus(StatusVenda status);

    List<Venda> findByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = {"cliente", "filial", "usuario", "itens"})
    @Query("select v from Venda v where v.id = :id")
    Optional<Venda> buscarComDetalhesPorId(@Param("id") Long id);

    @EntityGraph(attributePaths = {"cliente", "filial", "usuario", "itens"})
    @Query("select distinct v from Venda v order by v.criadoEm desc")
    List<Venda> listarComDetalhes();

    // =========================================================================
    // CONSULTAS ANALÍTICAS - PAINEL GERAL EXECUTIVO (DASHBOARD)
    // =========================================================================

    /**
     * Calcula o faturamento acumulado por período e filial (apenas vendas concretizadas).
     */
    @Query("""
        select coalesce(sum(v.valorTotal), 0)
        from Venda v
        where v.status = :status
          and v.criadoEm >= :inicio
          and v.criadoEm <= :fim
          and (:filialId is null or v.filial.id = :filialId)
    """)
    BigDecimal calcularFaturamentoPeriodo(
            @Param("status") StatusVenda status,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            @Param("filialId") Long filialId
    );

    /**
     * Conta a quantidade de transações concluídas no período para o cálculo do ticket médio.
     */
    @Query("""
        select count(v)
        from Venda v
        where v.status = :status
          and v.criadoEm >= :inicio
          and v.criadoEm <= :fim
          and (:filialId is null or v.filial.id = :filialId)
    """)
    Long contarTransacoesPeriodo(
            @Param("status") StatusVenda status,
            @Param("inicio") LocalDateTime inicio,
            @Param("fim") LocalDateTime fim,
            @Param("filialId") Long filialId
    );

    /**
     * Retorna as transações mais recentes para o feed ao vivo do dashboard.
     * Utiliza @EntityGraph para carregar cliente, filial e usuario em uma única consulta,
     * prevenindo LazyInitializationException com open-in-view=false.
     */
    @EntityGraph(attributePaths = {"cliente", "filial", "usuario"})
    @Query("""
        select v from Venda v
        where (:filialId is null or v.filial.id = :filialId)
        order by v.criadoEm desc
    """)
    List<Venda> buscarUltimasAtividades(@Param("filialId") Long filialId, Pageable pageable);
}