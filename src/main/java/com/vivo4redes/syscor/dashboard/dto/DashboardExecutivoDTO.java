package com.vivo4redes.syscor.dashboard.dto;

import java.math.BigDecimal;
import java.util.List;

public record DashboardExecutivoDTO(
        BigDecimal faturamentoMesAtual,
        BigDecimal metaOperacao,
        Double percentualAtingimentoMeta,
        Long volumeVendasTransacoes,
        BigDecimal ticketMedio,
        Long skusCriticosOuRuptura,
        List<EvolucaoMensalDTO> evolucaoVendas,
        List<AtividadeRecenteDTO> atividadesRecentes
) {}