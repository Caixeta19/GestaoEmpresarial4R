package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.enums.StatusCaixaSessao;
import com.vivo4redes.syscor.financeiro.model.CaixaSessao;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface CaixaSessaoRepository extends JpaRepository<CaixaSessao, Long>, JpaSpecificationExecutor<CaixaSessao> {

    /** Trava a linha do caixa (FOR UPDATE): sangrias e suprimentos simultâneos conferem o saldo um de cada vez. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CaixaSessao c where c.id = :id")
    Optional<CaixaSessao> buscarComTrava(@Param("id") Long id);

    /** O nome do caixa é gravado em maiúsculas e sem espaços nas pontas; casa com o índice único da V38. */
    boolean existsByFilialIdAndCaixaPdvAndDataAbertura(Long filialId, String caixaPdv, LocalDate dataAbertura);

    List<CaixaSessao> findByFilialIdAndDataAberturaAndStatusOrderByIdAsc(Long filialId, LocalDate dataAbertura, StatusCaixaSessao status);

    List<CaixaSessao> findByFilialIdAndStatusAndDataAberturaBeforeOrderByDataAberturaAsc(Long filialId, StatusCaixaSessao status, LocalDate data);

    Optional<CaixaSessao> findFirstByFilialIdAndCaixaPdvAndStatusOrderByDataAberturaDescIdDesc(
            Long filialId, String caixaPdv, StatusCaixaSessao status);
}
