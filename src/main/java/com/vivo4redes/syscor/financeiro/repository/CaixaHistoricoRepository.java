package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.model.CaixaHistorico;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaixaHistoricoRepository extends JpaRepository<CaixaHistorico, Long> {

    List<CaixaHistorico> findByCaixaSessaoIdOrderByEmAscIdAsc(Long caixaSessaoId);
}
