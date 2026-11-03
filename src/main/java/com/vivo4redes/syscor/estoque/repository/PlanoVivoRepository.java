package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.model.PlanoVivo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PlanoVivoRepository extends JpaRepository<PlanoVivo, Long> {

    /** Plano por nome normalizado ou por código (sistema / SAP), na execução informada. */
    @Query("""
        select p from PlanoVivo p
        where p.execucao.id = :execucaoId
          and (p.chavePlano = :chave or upper(p.codSistema) = :chave or upper(p.codSap) = :chave)
        order by p.id
        """)
    List<PlanoVivo> buscarPorNomeOuCodigo(@Param("execucaoId") Long execucaoId, @Param("chave") String chave);

    /** Combo "Plano Ativo / Linha": busca por trecho do nome e filtro opcional de disponível para alta. */
    @Query("""
        select p from PlanoVivo p
        where p.execucao.id = :execucaoId
          and (:busca = '' or p.chavePlano like concat('%', :busca, '%'))
          and (:disponivelAlta is null or p.disponivelAlta = :disponivelAlta)
        order by p.nomePlano
        """)
    List<PlanoVivo> listar(@Param("execucaoId") Long execucaoId,
                           @Param("busca") String busca,
                           @Param("disponivelAlta") Boolean disponivelAlta);

    long countByExecucaoId(Long execucaoId);
}
