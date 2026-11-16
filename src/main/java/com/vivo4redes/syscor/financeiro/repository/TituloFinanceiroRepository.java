package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TituloFinanceiroRepository extends JpaRepository<TituloFinanceiro, Long> {

    List<TituloFinanceiro> findByTipoOrderByVencimentoAsc(TipoTitulo tipo);

    List<TituloFinanceiro> findByTipoAndStatusOrderByVencimentoAsc(TipoTitulo tipo, StatusTitulo status);

    List<TituloFinanceiro> findAllByOrderByVencimentoAsc();

    List<TituloFinanceiro> findByVendaId(Long vendaId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TituloFinanceiro> findByTipoAndIdentificadorPagamento(TipoTitulo tipo, String identificadorPagamento);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<TituloFinanceiro> findByTipoAndCodigoBarras(TipoTitulo tipo, String codigoBarras);

    List<TituloFinanceiro> findByTipo(TipoTitulo tipo);

    /** Duplicidade por código de barras (só dígitos), ignorando as canceladas e a própria conta (na edição). */
    @Query("select count(t) > 0 from TituloFinanceiro t where t.tipo = :tipo and t.codigoBarras = :codigoBarras "
            + "and t.status <> :ignorado and (:ignorarId is null or t.id <> :ignorarId)")
    boolean existeCodigoBarras(@Param("tipo") TipoTitulo tipo, @Param("codigoBarras") String codigoBarras,
                               @Param("ignorado") StatusTitulo ignorado, @Param("ignorarId") Long ignorarId);

    /** Duplicidade provável: mesmo fornecedor (sem diferenciar caixa) + valor + vencimento, fora as canceladas. */
    @Query("select count(t) > 0 from TituloFinanceiro t where t.tipo = :tipo and lower(t.quem) = lower(:quem) "
            + "and t.valor = :valor and t.vencimento = :vencimento and t.status <> :ignorado "
            + "and (:ignorarId is null or t.id <> :ignorarId)")
    boolean existeMesmaCombinacao(@Param("tipo") TipoTitulo tipo, @Param("quem") String quem,
                                  @Param("valor") BigDecimal valor, @Param("vencimento") LocalDate vencimento,
                                  @Param("ignorado") StatusTitulo ignorado, @Param("ignorarId") Long ignorarId);

    /** Busca com trava pessimista (edição, baixa, cancelamento e reabertura concorrentes com a conciliação). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TituloFinanceiro t where t.id = :id and t.tipo = :tipo")
    Optional<TituloFinanceiro> buscarComTrava(@Param("id") Long id, @Param("tipo") TipoTitulo tipo);

    @Query("select t from TituloFinanceiro t where t.id = :id and t.tipo = :tipo")
    Optional<TituloFinanceiro> buscarPorIdETipo(@Param("id") Long id, @Param("tipo") TipoTitulo tipo);
}