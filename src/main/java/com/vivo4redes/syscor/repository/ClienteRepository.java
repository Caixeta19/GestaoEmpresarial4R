package com.vivo4redes.syscor.repository;

import java.util.Optional;
import java.util.UUID;

import com.vivo4redes.syscor.model.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<Cliente, UUID> {

    Optional<Cliente> findByCpfCnpj(String cpfCnpj);

    boolean existsByCpfCnpj(String cpfCnpj);
}