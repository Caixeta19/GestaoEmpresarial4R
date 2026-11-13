package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.model.CaixaLancamento;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CaixaLancamentoRepository extends JpaRepository<CaixaLancamento, Long> {

    List<CaixaLancamento> findByCaixaSessaoIdOrderByCriadoEmAscIdAsc(Long caixaSessaoId);

    Optional<CaixaLancamento> findByCaixaSessaoIdAndChaveIdempotencia(Long caixaSessaoId, String chaveIdempotencia);

    boolean existsByEstornadoDeId(Long lancamentoId);
}
