package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.enums.SituacaoExtrato;
import com.vivo4redes.syscor.financeiro.model.ExtratoLancamento;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExtratoLancamentoRepository extends JpaRepository<ExtratoLancamento, Long> {

    boolean existsByProviderAndIdExterno(String provider, String idExterno);

    /** Trava o lançamento para a conciliação e as ações manuais não pisarem uma na outra. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ExtratoLancamento l where l.id = :id")
    Optional<ExtratoLancamento> buscarComTrava(@Param("id") Long id);

    /**
     * Ids que ainda esperam decisão automática: NOVO e a fila (ambíguo e sem correspondência) sem conta ligada e
     * que ninguém desvinculou, desde a data informada.
     */
    @Query("select l.id from ExtratoLancamento l where l.situacao in :situacoes and l.desvinculado = false "
            + "and l.tituloId is null and l.dataLancamento >= :desde order by l.dataLancamento, l.id")
    List<Long> idsParaProcessar(@Param("situacoes") Collection<SituacaoExtrato> situacoes,
                                @Param("desde") LocalDate desde);

    @Query("select l from ExtratoLancamento l where (:situacao is null or l.situacao = :situacao) "
            + "and (cast(:de as date) is null or l.dataLancamento >= :de) "
            + "and (cast(:ate as date) is null or l.dataLancamento <= :ate) "
            + "order by l.dataLancamento desc, l.id desc")
    List<ExtratoLancamento> listar(@Param("situacao") SituacaoExtrato situacao, @Param("de") LocalDate de,
                                   @Param("ate") LocalDate ate, Pageable pagina);
}
