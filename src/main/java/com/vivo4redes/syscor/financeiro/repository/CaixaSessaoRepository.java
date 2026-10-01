package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.enums.StatusCaixaSessao;
import com.vivo4redes.syscor.financeiro.model.CaixaSessao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CaixaSessaoRepository extends JpaRepository<CaixaSessao, Long> {
    List<CaixaSessao> findByStatusOrderByAbertoEmDesc(StatusCaixaSessao status);

    List<CaixaSessao> findByStatusAndFilialIdOrderByAbertoEmDesc(StatusCaixaSessao status, Long filialId);

    @Query("SELECT c FROM CaixaSessao c WHERE c.filial.id = :filialId AND c.status = com.vivo4redes.syscor.financeiro.enums.StatusCaixaSessao.ABERTO")
    Optional<CaixaSessao> findSessaoAbertaPorFilial(@Param("filialId") Long filialId);
}