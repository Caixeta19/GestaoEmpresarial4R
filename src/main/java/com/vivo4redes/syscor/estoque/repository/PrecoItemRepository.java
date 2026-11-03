package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.estoque.model.PrecoItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface PrecoItemRepository extends JpaRepository<PrecoItem, Long> {

    /** Preços de um item em uma execução, para os grupos informados (o do plano e SEM_PLANO). */
    @Query("""
        select p from PrecoItem p
        where p.execucao.id = :execucaoId and p.chaveNome = :chaveNome
          and p.tabela = :tabela and p.grupoPlano in :grupos
        order by p.id
        """)
    List<PrecoItem> buscar(@Param("execucaoId") Long execucaoId,
                           @Param("chaveNome") String chaveNome,
                           @Param("tabela") TabelaPreco tabela,
                           @Param("grupos") Collection<GrupoPlano> grupos);

    List<PrecoItem> findByExecucaoIdAndChaveNomeOrderByTabelaAscGrupoPlanoAsc(Long execucaoId, String chaveNome);

    List<PrecoItem> findByExecucaoIdAndTabelaAndGrupoPlanoOrderByNomeComercialAsc(Long execucaoId, TabelaPreco tabela,
                                                                                 GrupoPlano grupoPlano);

    /** Preço de referência por item (chave -> preço): REGULAR, no grupo informado (PRE ou SEM_PLANO). */
    @Query("""
        select p.chaveNome, p.preco from PrecoItem p
        where p.execucao.id = :execucaoId and p.tabela = com.vivo4redes.syscor.estoque.enums.TabelaPreco.REGULAR
          and p.grupoPlano = :grupo
        order by p.id
        """)
    List<Object[]> precosDeReferencia(@Param("execucaoId") Long execucaoId, @Param("grupo") GrupoPlano grupo);

    @Query("select distinct p.grupoPlano from PrecoItem p where p.execucao.id = :execucaoId")
    List<GrupoPlano> gruposDistintos(@Param("execucaoId") Long execucaoId);

    long countByExecucaoId(Long execucaoId);
}
