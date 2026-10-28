package com.vivo4redes.syscor.estoque.repository;

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

/** Substitui ProdutoRepository e ItemEstoqueRepository. */
@Repository
public interface EstoqueGeralRepository extends JpaRepository<EstoqueGeral, Long> {

    Optional<EstoqueGeral> findBySerial(String serial);

    boolean existsBySerial(String serial);

    List<EstoqueGeral> findByStatus(StatusEstoque status);

    long countByStatus(StatusEstoque status);

    /** Baixa de um serial: trava a linha para duas vendas simultâneas não pegarem o mesmo IMEI. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EstoqueGeral e WHERE e.serial = :serial AND e.status = :status")
    Optional<EstoqueGeral> findBySerialAndStatusForUpdate(@Param("serial") String serial,
                                                          @Param("status") StatusEstoque status);

    /** Baixa por quantidade (acessórios): trava a linha pelo id. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM EstoqueGeral e WHERE e.id = :id")
    Optional<EstoqueGeral> findByIdForUpdate(@Param("id") Long id);

    /** PDV: aparelhos (sem código de acessório) disponíveis, por trecho do serial ou do modelo. */
    @Query("SELECT e FROM EstoqueGeral e WHERE e.status = :status "
            + "AND (e.codigoAcessorio IS NULL OR e.codigoAcessorio = '') "
            + "AND (LOWER(e.serial) LIKE LOWER(CONCAT('%', :busca, '%')) "
            + "  OR LOWER(e.modeloComercial) LIKE LOWER(CONCAT('%', :busca, '%'))) "
            + "ORDER BY e.modeloComercial, e.serial")
    List<EstoqueGeral> buscarAparelhosDisponiveis(@Param("status") StatusEstoque status,
                                                  @Param("busca") String busca,
                                                  Pageable pageable);

    /** PDV: acessórios disponíveis, por trecho do código ou do modelo. */
    @Query("SELECT e FROM EstoqueGeral e WHERE e.status = :status "
            + "AND e.codigoAcessorio IS NOT NULL AND e.codigoAcessorio <> '' "
            + "AND (LOWER(e.codigoAcessorio) LIKE LOWER(CONCAT('%', :busca, '%')) "
            + "  OR LOWER(e.modeloComercial) LIKE LOWER(CONCAT('%', :busca, '%'))) "
            + "ORDER BY e.modeloComercial, e.codigoAcessorio")
    List<EstoqueGeral> buscarAcessoriosDisponiveis(@Param("status") StatusEstoque status,
                                                   @Param("busca") String busca,
                                                   Pageable pageable);
}