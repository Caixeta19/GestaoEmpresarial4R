package com.vivo4redes.syscor.venda.repository;

import com.vivo4redes.syscor.venda.model.PagamentoVenda;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PagamentoVendaRepository extends JpaRepository<PagamentoVenda, Long> {
    List<PagamentoVenda> findByVendaIdOrderByIdAsc(Long vendaId);
}