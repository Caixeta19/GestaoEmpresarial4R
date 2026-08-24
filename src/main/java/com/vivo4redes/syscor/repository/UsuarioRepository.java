package com.vivo4redes.syscor.repository;

import java.util.Optional;
import java.util.UUID;

import com.vivo4redes.syscor.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsuarioRepository extends JpaRepository<Usuario, UUID> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}