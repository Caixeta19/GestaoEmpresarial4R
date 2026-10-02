package com.vivo4redes.syscor.venda.repository;

import com.vivo4redes.syscor.venda.model.Filial;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FilialRepository extends JpaRepository<Filial, Long> {

    boolean existsByCodigoIgnoreCase(String codigo);

    List<Filial> findByAtivoTrueOrderByNomeAsc();
}