package com.vivo4redes.syscor.financeiro.repository;

import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TituloFinanceiroRepository extends JpaRepository<TituloFinanceiro, Long> {
    List<TituloFinanceiro> findByTipoOrderByVencimentoAsc(TipoTitulo tipo);

    List<TituloFinanceiro> findByTipoAndStatusOrderByVencimentoAsc(TipoTitulo tipo, StatusTitulo status);

    List<TituloFinanceiro> findAllByOrderByVencimentoAsc();
}