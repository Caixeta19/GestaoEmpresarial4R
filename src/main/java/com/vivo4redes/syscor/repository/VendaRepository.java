package com.vivo4redes.syscor.repository;

import com.vivo4redes.syscor.enums.StatusVenda;
import com.vivo4redes.syscor.model.Venda;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VendaRepository extends JpaRepository<Venda, Long> {

    List<Venda> findByClienteId(Long clienteId);

    List<Venda> findByStatus(StatusVenda status);
}