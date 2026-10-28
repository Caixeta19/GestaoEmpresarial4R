package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.dto.response.EstoqueConsolidadoModeloDTO;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EstoqueGeralRepository extends JpaRepository<EstoqueGeral, Long> {

    // 1. Buscas e validações por serial
    boolean existsBySerial(String serial);

    List<EstoqueGeral> findBySerialIn(List<String> seriais);

    List<EstoqueGeral> findBySerialInAndStatusNot(List<String> seriais, StatusEstoque status);

    Optional<EstoqueGeral> findBySerialAndStatus(String serial, StatusEstoque status);

    List<EstoqueGeral> findByStatus(StatusEstoque statusEstoque);

    // 2. Buscas e atualizações por modelo
    @Query("SELECT e FROM EstoqueGeral e WHERE e.modeloComercial IN :modelos AND e.status <> :status")
    List<EstoqueGeral> findByModeloIn(@Param("modelos") List<String> modelos, @Param("status") StatusEstoque status);

    @Query("SELECT DISTINCT e.modeloComercial FROM EstoqueGeral e WHERE e.modeloComercial IN :modelos")
    List<String> findModelosExistentes(@Param("modelos") List<String> modelos);

    // 3. Bloqueio pessimista para baixa transacional no PDV (por serial ou por ID)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EstoqueGeral e WHERE e.serial = :serial")
    Optional<EstoqueGeral> findBySerialForUpdate(@Param("serial") String serial);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EstoqueGeral e WHERE e.id = :id")
    Optional<EstoqueGeral> findByIdForUpdate(@Param("id") Long id);

    // 4. Consultas de itens disponíveis com filtro por termo e filial
    @Query("SELECT e FROM EstoqueGeral e WHERE e.status = :status " +
            "AND (:termo IS NULL OR :termo = '' OR e.serial LIKE %:termo% OR e.modeloComercial LIKE %:termo% OR e.marca LIKE %:termo% OR e.codigoAcessorio LIKE %:termo%) " +
            "AND (:filial IS NULL OR :filial = '' OR e.filialEstoque = :filial)")
    List<EstoqueGeral> buscarDisponiveis(
            @Param("status") StatusEstoque status,
            @Param("termo") String termo,
            @Param("filial") String filial,
            Pageable pageable
    );

    // 5. Consolidação por modelo (retornando o DTO correspondente)
    @Query("NEW com.vivo4redes.syscor.estoque.dto.response.EstoqueConsolidadoModeloDTO(" +
            "e.modeloComercial, e.categoria, COUNT(e), SUM(CASE WHEN e.status = 'DISPONIVEL' THEN 1 ELSE 0 END)) " +
            "FROM EstoqueGeral e GROUP BY e.modeloComercial, e.categoria")
    List<EstoqueConsolidadoModeloDTO> consolidarPorModelo();
}