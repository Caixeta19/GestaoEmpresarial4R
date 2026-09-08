package com.vivo4redes.syscor.venda.repository;

import com.vivo4redes.syscor.venda.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByCpfCnpj(String cpfCnpj);

    boolean existsByCpfCnpj(String cpfCnpj);
}