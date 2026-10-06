package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

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
}