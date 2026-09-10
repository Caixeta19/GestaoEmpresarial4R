package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.model.InventarioExecucao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InventarioExecucaoRepository extends JpaRepository<InventarioExecucao, Long> {
}