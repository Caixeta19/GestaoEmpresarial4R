package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.model.CaixaTentativaFechamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CaixaTentativaFechamentoRepository extends JpaRepository<CaixaTentativaFechamento, Long> {

    long countByCaixaSessaoIdAndDia(Long caixaSessaoId, LocalDate dia);

    List<CaixaTentativaFechamento> findByCaixaSessaoIdOrderByEmAscIdAsc(Long caixaSessaoId);
}
