package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.model.InventarioItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InventarioItemRepository extends JpaRepository<InventarioItem, Long> {
    List<InventarioItem> findByExecucaoIdOrderByResultadoAscSerialSapAsc(Long execucaoId);
}