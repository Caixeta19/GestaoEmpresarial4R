package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.model.TransacaoBancaria;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransacaoBancariaRepository extends JpaRepository<TransacaoBancaria, Long> {
    boolean existsByProviderAndIdExterno(String provider, String idExterno);
}