package com.vivo4redes.syscor.estoque.service.diagnostico;

import java.time.LocalDate;
import java.util.List;

/**
 * Contexto de um serial enviado à IA. Só dados de produto e de estoque: nunca nome, CPF ou telefone de cliente.
 * A venda vai só com número, data e status.
 */
public record ItemParaDiagnostico(
        String imei,
        String resultado,
        String observacao,
        String material,
        String descricao,
        boolean naIq09DaLoja,
        String statusSistemaSap,
        String statusUsuarioSap,
        LocalDate modificadoEmSap,
        String modificadoPorSap,
        String statusNoEstoque,
        String filialNoEstoque,
        LocalDate dataCompra,
        String filialCompra,
        List<String> outrosCentrosIq09,
        boolean bipado,
        String justificativa,
        UltimaVenda ultimaVenda
) {

    public record UltimaVenda(Long numeroVenda, LocalDate data, String status) {
    }
}
