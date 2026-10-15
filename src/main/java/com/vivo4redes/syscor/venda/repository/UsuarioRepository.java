package com.vivo4redes.syscor.venda.repository;

import aj.org.objectweb.asm.commons.InstructionAdapter;
import com.vivo4redes.syscor.venda.model.Usuario;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByEmailIgnoreCase(String email);
    Optional<Usuario> findFirstByLoginIgnoreCase(String login);

    boolean existsByEmailIgnoreCase(@NotBlank(message = "email é obrigatório") @Email(message = "email inválido") String email);

    List<Usuario> findByAtivoTrueOrderByNomeAsc();
}