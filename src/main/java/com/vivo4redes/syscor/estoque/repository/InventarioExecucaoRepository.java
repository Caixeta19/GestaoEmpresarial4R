package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.enums.StatusInventario;
import com.vivo4redes.syscor.estoque.model.InventarioExecucao;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventarioExecucaoRepository extends JpaRepository<InventarioExecucao, Long> {

    Optional<InventarioExecucao> findFirstByFilialIdAndStatusIn(Long filialId, Collection<StatusInventario> status);

    @Query("""
        select e from InventarioExecucao e left join fetch e.filial
        where (:filialId is null or e.filial.id = :filialId)
          and e.status in :status
        order by e.criadoEm desc, e.id desc
        """)
    List<InventarioExecucao> listar(@Param("filialId") Long filialId, @Param("status") Collection<StatusInventario> status);

    /** Fechar/cancelar: espera as bipagens em curso e impede novas (que usam {@link #buscarParaBipagem}). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from InventarioExecucao e where e.id = :id")
    Optional<InventarioExecucao> buscarParaFechamento(@Param("id") Long id);

    /** Bipagem: trava compartilhada (FOR SHARE). Várias bipagens correm juntas; o fechamento espera por elas. */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select e from InventarioExecucao e where e.id = :id")
    Optional<InventarioExecucao> buscarParaBipagem(@Param("id") Long id);
}
