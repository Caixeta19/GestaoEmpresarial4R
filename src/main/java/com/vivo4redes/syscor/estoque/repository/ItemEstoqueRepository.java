package com.vivo4redes.syscor.estoque.repository;

import com.vivo4redes.syscor.estoque.enums.StatusSerial;
import com.vivo4redes.syscor.estoque.model.ItemEstoque;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ItemEstoqueRepository extends JpaRepository<ItemEstoque, Long> {

    List<ItemEstoque> findByProdutoIdAndStatus(Long produtoId, StatusSerial status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ItemEstoque i WHERE i.serialImei = :serial AND i.status = :status")
    Optional<ItemEstoque> findBySerialImeiAndStatusForUpdate(
            @Param("serial") String serial,
            @Param("status") StatusSerial status
    );

    boolean existsBySerialImei(String serialImei);

    long countByProdutoIdAndStatus(Long produtoId, StatusSerial status);
}