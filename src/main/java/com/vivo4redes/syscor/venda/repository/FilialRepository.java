package com.vivo4redes.syscor.venda.repository;

import com.vivo4redes.syscor.venda.model.Filial;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FilialRepository extends JpaRepository<Filial, Long> {

    boolean existsByCodigoIgnoreCase(String codigo);

    Optional<Filial> findByCodigoIgnoreCase(String codigo);

    List<Filial> findByAtivoTrueOrderByNomeAsc();

    List<Filial> findAllByOrderByNomeAsc();

    /** Centros SAP já cadastrados (maiúsculos): o resumo do inventário lista os centros do arquivo que não estão aqui. */
    @Query("select upper(f.centroSap) from Filial f where f.centroSap is not null")
    List<String> listarCentrosSap();

    List<Filial> findByCentroSapIsNotNull();

    /** Filiais cujo nome é igual ao texto (sem diferenciar maiúsculas nem espaços das pontas): o vínculo usuário → filial. */
    @Query("select f.id from Filial f where upper(trim(f.nome)) = upper(trim(:nome))")
    List<Long> listarIdsPorNome(@org.springframework.data.repository.query.Param("nome") String nome);
}
