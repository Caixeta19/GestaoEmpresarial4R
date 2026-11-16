package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.enums.AcaoTitulo;
import com.vivo4redes.syscor.financeiro.model.TituloHistorico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TituloHistoricoRepository extends JpaRepository<TituloHistorico, Long> {

    List<TituloHistorico> findByTituloIdOrderByEmAscIdAsc(Long tituloId);

    boolean existsByTituloIdAndAcao(Long tituloId, AcaoTitulo acao);
}
