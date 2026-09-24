package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.enums.StatusCaixaSessao;
import com.vivo4redes.syscor.financeiro.model.CaixaSessao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CaixaSessaoRepository extends JpaRepository<CaixaSessao, Long> {
    List<CaixaSessao> findByStatusOrderByAbertoEmDesc(StatusCaixaSessao status);

    List<CaixaSessao> findByStatusAndFilialIdOrderByAbertoEmDesc(StatusCaixaSessao status, Long filialId);
}