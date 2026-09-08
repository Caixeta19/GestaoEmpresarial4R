package com.vivo4redes.syscor.venda.repository;

import com.vivo4redes.syscor.venda.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCaseAndAtivoTrue(String email);

    boolean existsByEmailIgnoreCase(String email);
}
