package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.model.ProdutoVivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProdutoVivoRepository extends JpaRepository<ProdutoVivo, Long> {

    @Query("select p from ProdutoVivo p where p.execucao.id = :execucaoId and upper(p.codSap) = :codSap order by p.id")
    List<ProdutoVivo> buscarPorCodSap(@Param("execucaoId") Long execucaoId, @Param("codSap") String codSap);

    /** Cod_Sap (maiúsculo) -> chave do Nome Comercial, para resolver o estoque em lote. */
    @Query("select upper(p.codSap), p.chaveNome from ProdutoVivo p where p.execucao.id = :execucaoId")
    List<Object[]> indiceCodSap(@Param("execucaoId") Long execucaoId);

    long countByExecucaoId(Long execucaoId);
}
