package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.model.TabelaPrecoExecucao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TabelaPrecoExecucaoRepository extends JpaRepository<TabelaPrecoExecucao, Long> {
    Optional<TabelaPrecoExecucao> findTopByOrderByCriadoEmDesc();
}