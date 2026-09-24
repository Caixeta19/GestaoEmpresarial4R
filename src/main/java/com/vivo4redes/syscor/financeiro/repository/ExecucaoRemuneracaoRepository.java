package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.model.ExecucaoRemuneracao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ExecucaoRemuneracaoRepository extends JpaRepository<ExecucaoRemuneracao, Long> {
}