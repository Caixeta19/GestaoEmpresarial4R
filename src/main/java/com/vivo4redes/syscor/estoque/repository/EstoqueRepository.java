package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.model.Estoque;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EstoqueRepository extends JpaRepository<Estoque, Long> {

    Optional<Estoque> findBySerial(String serial);

    @Query("SELECT e FROM Estoque e WHERE LOWER(e.serial) LIKE LOWER(CONCAT('%', :busca, '%')) OR LOWER(e.modeloComercial) LIKE LOWER(CONCAT('%', :busca, '%'))")
    List<Estoque> buscarPorSerialOuModelo(@Param("busca") String busca, Pageable pageable);
}