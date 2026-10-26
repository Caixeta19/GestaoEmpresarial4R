package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.dto.response.EstoqueConsolidadoModeloDTO;
import com.vivo4redes.syscor.estoque.enums.StatusEstoque;
import com.vivo4redes.syscor.estoque.model.EstoqueGeral;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface EstoqueGeralRepository extends JpaRepository<EstoqueGeral, Long> {

    /** Serial exato + status (ex.: conferir se um IMEI lido no PDV está DISPONIVEL). */
    Optional<EstoqueGeral> findBySerialAndStatus(String serial, StatusEstoque status);

    Optional<EstoqueGeral> findBySerial(String serial);

    boolean existsBySerial(String serial);

    List<EstoqueGeral> findByStatus(StatusEstoque status);

    /** Carga em lote para o upsert da importação (evita N+1). */
    List<EstoqueGeral> findBySerialIn(Collection<String> seriais);

    /** Atualização de preços: ignora o que já foi vendido (histórico não muda). */
    List<EstoqueGeral> findBySerialInAndStatusNot(Collection<String> seriais, StatusEstoque status);

    @Query("SELECT e FROM EstoqueGeral e WHERE UPPER(e.modeloComercial) IN :modelos AND e.status <> :excluido")
    List<EstoqueGeral> findByModeloIn(@Param("modelos") Collection<String> modelosEmMaiusculas,
                                      @Param("excluido") StatusEstoque excluido);

    /** Modelos (em maiúsculas) que existem no estoque, em qualquer status. */
    @Query("SELECT DISTINCT UPPER(e.modeloComercial) FROM EstoqueGeral e WHERE UPPER(e.modeloComercial) IN :modelos")
    List<String> findModelosExistentes(@Param("modelos") Collection<String> modelosEmMaiusculas);

    /**
     * Baixa no PDV: trava a linha (SELECT ... FOR UPDATE) com timeout de 5s —
     * duas vendas simultâneas do mesmo serial se enfileiram em vez de colidir.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
    @Query("SELECT e FROM EstoqueGeral e WHERE e.serial = :serial")
    Optional<EstoqueGeral> findBySerialForUpdate(@Param("serial") String serial);

    /**
     * Itens disponíveis para a interface de vendas, por trecho de serial, modelo,
     * marca ou código de acessório. {@code filial} vazio = todas as filiais.
     */
    @Query("SELECT e FROM EstoqueGeral e WHERE e.status = :status "
            + "AND (:filial = '' OR e.filialEstoque = :filial) "
            + "AND (LOWER(e.serial) LIKE LOWER(CONCAT('%', :busca, '%')) "
            + "  OR LOWER(e.modeloComercial) LIKE LOWER(CONCAT('%', :busca, '%')) "
            + "  OR LOWER(e.marca) LIKE LOWER(CONCAT('%', :busca, '%')) "
            + "  OR LOWER(e.codigoAcessorio) LIKE LOWER(CONCAT('%', :busca, '%'))) "
            + "ORDER BY e.modeloComercial, e.serial")
    List<EstoqueGeral> buscarDisponiveis(@Param("status") StatusEstoque status,
                                         @Param("busca") String busca,
                                         @Param("filial") String filial,
                                         Pageable pageable);

    /** Saldo por modelo (base do consolidado, dos alertas e do dashboard). */
    @Query("SELECT new com.vivo4redes.syscor.estoque.dto.response.EstoqueConsolidadoModeloDTO("
            + "e.modeloComercial, e.marca, e.categoria, "
            + "SUM(CASE WHEN e.status = com.vivo4redes.syscor.estoque.enums.StatusEstoque.DISPONIVEL THEN e.quantidade ELSE 0 END), "
            + "SUM(CASE WHEN e.status = com.vivo4redes.syscor.estoque.enums.StatusEstoque.RESERVADO THEN e.quantidade ELSE 0 END), "
            + "SUM(CASE WHEN e.status = com.vivo4redes.syscor.estoque.enums.StatusEstoque.AVARIADO THEN e.quantidade ELSE 0 END), "
            + "MIN(e.valorVenda)) "
            + "FROM EstoqueGeral e GROUP BY e.modeloComercial, e.marca, e.categoria "
            + "ORDER BY e.modeloComercial")
    List<EstoqueConsolidadoModeloDTO> consolidarPorModelo();
}