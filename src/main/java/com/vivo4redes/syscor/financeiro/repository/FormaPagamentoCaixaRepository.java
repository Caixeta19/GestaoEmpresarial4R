package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.model.FormaPagamentoCaixa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FormaPagamentoCaixaRepository extends JpaRepository<FormaPagamentoCaixa, Long> {
    List<FormaPagamentoCaixa> findByCaixaSessaoId(Long caixaSessaoId);
}