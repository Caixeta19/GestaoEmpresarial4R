package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;
import com.vivo4redes.syscor.estoque.model.InventarioItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface InventarioItemRepository extends JpaRepository<InventarioItem, Long> {

    Optional<InventarioItem> findByExecucaoIdAndSerialNormalizado(Long execucaoId, String serialNormalizado);

    Optional<InventarioItem> findByIdAndExecucaoId(Long id, Long execucaoId);

    List<InventarioItem> findByExecucaoId(Long execucaoId);

    List<InventarioItem> findByExecucaoIdAndResultadoIn(Long execucaoId, Collection<ResultadoConciliacaoInventario> resultados);

    long countByExecucaoIdAndResultadoAndJustificativaIsNull(Long execucaoId, ResultadoConciliacaoInventario resultado);

    /** Abas da tela: filtro por resultado e busca por serial, material ou descrição ({@code busca} já em %MAIÚSCULAS%). */
    @Query(value = """
        select i from InventarioItem i left join fetch i.bipadoPor
        where i.execucao.id = :execucaoId
          and i.resultado in :resultados
          and (:busca is null
               or i.serialNormalizado like :busca
               or upper(coalesce(i.materialSap, '')) like :busca
               or upper(coalesce(i.denominacaoSap, '')) like :busca)
        """,
            countQuery = """
        select count(i) from InventarioItem i
        where i.execucao.id = :execucaoId
          and i.resultado in :resultados
          and (:busca is null
               or i.serialNormalizado like :busca
               or upper(coalesce(i.materialSap, '')) like :busca
               or upper(coalesce(i.denominacaoSap, '')) like :busca)
        """)
    Page<InventarioItem> buscarItens(@Param("execucaoId") Long execucaoId,
                                     @Param("resultados") Collection<ResultadoConciliacaoInventario> resultados,
                                     @Param("busca") String busca,
                                     Pageable pageable);

    @Query("""
        select i from InventarioItem i left join fetch i.bipadoPor
        where i.execucao.id = :execucaoId and i.bipadoEm is not null
        order by i.bipadoEm desc, i.id desc
        """)
    List<InventarioItem> ultimasBipagens(@Param("execucaoId") Long execucaoId, Pageable pageable);

    @Query("""
        select i from InventarioItem i left join fetch i.bipadoPor
        where i.execucao.id = :execucaoId
        order by i.resultado, i.serialNormalizado
        """)
    List<InventarioItem> listarParaExportacao(@Param("execucaoId") Long execucaoId);

    /** [resultado, quantidade, esperados, bipados] por resultado: base dos contadores ao vivo. */
    @Query("""
        select i.resultado, count(i),
               sum(case when i.esperadoIq09 = true or i.esperadoSistema = true then 1 else 0 end),
               sum(case when i.bipadoEm is not null then 1 else 0 end)
        from InventarioItem i
        where i.execucao.id = :execucaoId
        group by i.resultado
        """)
    List<Object[]> contarPorResultado(@Param("execucaoId") Long execucaoId);
}
