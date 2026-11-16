package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.enums.StatusSincronizacao;
import com.vivo4redes.syscor.financeiro.model.ExtratoSincronizacao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ExtratoSincronizacaoRepository extends JpaRepository<ExtratoSincronizacao, Long> {

    Optional<ExtratoSincronizacao> findFirstByOrderByFimDescIdDesc();

    Optional<ExtratoSincronizacao> findFirstByStatusOrderByFimDescIdDesc(StatusSincronizacao status);
}
