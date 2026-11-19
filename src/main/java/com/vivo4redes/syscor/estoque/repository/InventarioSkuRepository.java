package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.enums.ResultadoSku;
import com.vivo4redes.syscor.estoque.model.InventarioSku;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Conferência por SKU. "Visível" = tem alguma coisa em alguma fonte (linha da MB52, IQ09, sistema, bipagem ou contagem
 * manual); linhas que zeraram (ex.: sobra desfeita) ficam fora da tela, dos totais e do CSV.
 */
@Repository
public interface InventarioSkuRepository extends JpaRepository<InventarioSku, Long> {

    String VISIVEL = "(s.temLinhaMb52 = true or s.qtdIq09 > 0 or s.qtdSistema > 0 or s.qtdBipada > 0 or s.qtdContagemManual is not null)";

    String FILTRO = """
            where s.execucao.id = :execucaoId
              and s.resultado in :resultados
              and (:somenteDiferenca = false or s.resultado <> com.vivo4redes.syscor.estoque.enums.ResultadoSku.CONFERIDO)
              and (:busca is null
                   or upper(coalesce(s.materialNormalizado, '')) like :busca
                   or upper(coalesce(s.materialSap, '')) like :busca
                   or upper(coalesce(s.denominacao, '')) like :busca)
              and\s""" + VISIVEL;

    List<InventarioSku> findByExecucaoId(Long execucaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InventarioSku s where s.execucao.id = :execucaoId and s.materialNormalizado = :sku")
    Optional<InventarioSku> travar(@Param("execucaoId") Long execucaoId, @Param("sku") String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InventarioSku s where s.execucao.id = :execucaoId and s.materialNormalizado is null")
    Optional<InventarioSku> travarNaoIdentificado(@Param("execucaoId") Long execucaoId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from InventarioSku s where s.id = :id and s.execucao.id = :execucaoId")
    Optional<InventarioSku> travarPorId(@Param("execucaoId") Long execucaoId, @Param("id") Long id);

    /**
     * Página da tela. {@code ordenacao}: {@code valor} (|valor estimado| desc), {@code quantidade} (|MB52 − sistema| desc)
     * ou qualquer outro valor = por SKU.
     */
    @Query(value = "select s from InventarioSku s left join fetch s.contadoPor " + FILTRO + """
             order by
               case when :ordenacao = 'valor' then abs(coalesce(s.valorDiferenca, 0)) else 0 end desc,
               case when :ordenacao = 'quantidade' then abs(s.difSapSistema) else 0 end desc,
               case when s.materialNormalizado is null then 1 else 0 end,
               s.materialNormalizado
            """,
            countQuery = "select count(s) from InventarioSku s " + FILTRO)
    Page<InventarioSku> buscar(@Param("execucaoId") Long execucaoId,
                               @Param("resultados") Collection<ResultadoSku> resultados,
                               @Param("somenteDiferenca") boolean somenteDiferenca,
                               @Param("busca") String busca,
                               @Param("ordenacao") String ordenacao,
                               Pageable pageable);

    @Query("select s from InventarioSku s left join fetch s.contadoPor where s.execucao.id = :execucaoId and " + VISIVEL + """
             order by s.resultado, case when s.materialNormalizado is null then 1 else 0 end, s.materialNormalizado
            """)
    List<InventarioSku> listarParaExportacao(@Param("execucaoId") Long execucaoId);

    /** [resultado, quantidade] das linhas visíveis. */
    @Query("select s.resultado, count(s) from InventarioSku s where s.execucao.id = :execucaoId and " + VISIVEL
            + " group by s.resultado")
    List<Object[]> contarPorResultado(@Param("execucaoId") Long execucaoId);

    /** Soma de |valor estimado da diferença| das linhas visíveis. */
    @Query("select coalesce(sum(abs(s.valorDiferenca)), 0) from InventarioSku s where s.execucao.id = :execucaoId and " + VISIVEL)
    BigDecimal somarValorDiferencas(@Param("execucaoId") Long execucaoId);
}
