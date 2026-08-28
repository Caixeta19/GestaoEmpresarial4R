package com.vivo4redes.syscor.repository;
import com.vivo4redes.syscor.enums.StatusVenda;
import com.vivo4redes.syscor.model.Venda;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

/**
 * Como spring.jpa.open-in-view=false (correto — evita sessão aberta pela
 * request toda), o DTO de resposta é montado no controller DEPOIS que a
 * transação do service já fechou. Isso quebra em qualquer relação LAZY
 * (cliente, filial, usuario, itens) com LazyInitializationException.
 * Solução: buscar a Venda já com tudo carregado, dentro da transação,
 * via @EntityGraph — sem precisar tornar as relações EAGER na entidade
 * (o que causaria N+1 em toda consulta que não precisa desses dados).
 */
public interface VendaRepository extends JpaRepository<Venda, Long> {

    List<Venda> findByClienteId(Long clienteId);

    List<Venda> findByStatus(StatusVenda status);

    List<Venda> findByUsuarioId(Long usuarioId);

    @EntityGraph(attributePaths = {"cliente", "filial", "usuario", "itens"})
    @Query("select v from Venda v where v.id = :id")
    Optional<Venda> buscarComDetalhesPorId(@Param("id") Long id);

    @EntityGraph(attributePaths = {"cliente", "filial", "usuario", "itens"})
    @Query("select distinct v from Venda v order by v.criadoEm desc")
    List<Venda> listarComDetalhes();
}