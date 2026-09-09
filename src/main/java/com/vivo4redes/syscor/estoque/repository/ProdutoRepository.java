package com.vivo4redes.syscor.estoque.repository;
import com.vivo4redes.syscor.estoque.model.Produto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProdutoRepository extends JpaRepository<Produto, Long> {
    Optional<Produto> findBySku(String sku);
    Optional<Produto> findFirstByDescricaoContainingIgnoreCase(String descricao);
    boolean existsBySkuIgnoreCase(String sku);
    List<Produto> findAllByOrderByDescricaoAsc();
}