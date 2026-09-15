package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.model.ProdutoPrecoPlano;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProdutoPrecoPlanoRepository extends JpaRepository<ProdutoPrecoPlano, Long> {
    List<ProdutoPrecoPlano> findByExecucaoIdOrderByEncontradoAscNomeComercialAsc(Long execucaoId);

    List<ProdutoPrecoPlano> findByProdutoIdOrderByOfertaComunicacaoAsc(Long produtoId);

    /** Tabs disponíveis (nomes de oferta como vêm da planilha, sem agrupar) na execução mais recente. */
    @Query("SELECT DISTINCT p.ofertaComunicacao FROM ProdutoPrecoPlano p " +
            "WHERE p.execucao.id = :execucaoId AND p.ofertaComunicacao IS NOT NULL " +
            "ORDER BY p.ofertaComunicacao")
    List<String> listarOfertasDistintas(@Param("execucaoId") Long execucaoId);

    /** Catálogo de produtos com preço para uma oferta específica, na execução mais recente. */
    @Query("SELECT p FROM ProdutoPrecoPlano p " +
            "WHERE p.execucao.id = :execucaoId AND p.ofertaComunicacao = :oferta AND p.encontrado = true " +
            "ORDER BY p.produto.descricao")
    List<ProdutoPrecoPlano> listarPorExecucaoEOferta(@Param("execucaoId") Long execucaoId, @Param("oferta") String oferta);
}