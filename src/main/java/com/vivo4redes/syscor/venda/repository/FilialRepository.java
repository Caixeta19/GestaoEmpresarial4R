package com.vivo4redes.syscor.venda.repository;

import com.vivo4redes.syscor.venda.model.Filial;
import jakarta.validation.constraints.NotBlank;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FilialRepository extends JpaRepository<Filial, Long> {
    boolean existsByCodigoIgnoreCase(@NotBlank(message = "codigo é obrigatório") String codigo);
}