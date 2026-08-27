package com.vivo4redes.syscor.repository;

import com.vivo4redes.syscor.enums.StatusVenda;
import com.vivo4redes.syscor.model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface VendaRepository extends JpaRepository<Venda, Long> {

    /**
     * Listagem geral, com filtros opcionais de clienteId e/ou status.
     * Sempre com cliente/filial/vendedor/itens já inicializados (mesmo motivo
     * de {@link #buscarComRelacionamentosPorId}) — a listagem também é mapeada
     * para VendaResponseDTO fora da transação do service.
     */
    @Query("""
            SELECT DISTINCT v FROM Venda v
            JOIN FETCH v.cliente
            JOIN FETCH v.filial
            JOIN FETCH v.vendedor
            LEFT JOIN FETCH v.itens
            WHERE (:clienteId IS NULL OR v.cliente.id = :clienteId)
              AND (:status IS NULL OR v.status = :status)
            ORDER BY v.id
            """)
    List<Venda> listarComRelacionamentos(@Param("clienteId") Long clienteId,
                                         @Param("status") StatusVenda status);

    /**
     * Carrega a venda com cliente, filial, vendedor e itens já inicializados
     * (JOIN FETCH), evitando LazyInitializationException quando o DTO é montado
     * fora da transação (spring.jpa.open-in-view: false). Um único FETCH de
     * coleção (itens) combinado com múltiplos FETCH de *ToOne não gera produto
     * cartesiano problemático aqui — apenas duplica linhas de itens, resolvido
     * pelo Set/distinct do Hibernate ao popular a coleção.
     */
    @Query("""
            SELECT DISTINCT v FROM Venda v
            JOIN FETCH v.cliente
            JOIN FETCH v.filial
            JOIN FETCH v.vendedor
            LEFT JOIN FETCH v.itens
            WHERE v.id = :id
            """)
    Optional<Venda> buscarComRelacionamentosPorId(@Param("id") Long id);
}