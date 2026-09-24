package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.model.ItemRemuneracao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRemuneracaoRepository extends JpaRepository<ItemRemuneracao, Long> {
    List<ItemRemuneracao> findByExecucaoIdOrderByStatusAscClienteVivoAsc(Long execucaoId);
}