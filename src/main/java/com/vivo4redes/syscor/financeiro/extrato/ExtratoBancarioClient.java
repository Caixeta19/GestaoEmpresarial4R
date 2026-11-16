package com.vivo4redes.syscor.financeiro.extrato;

import java.time.LocalDate;
import java.util.List;

/**
 * Porta do extrato bancário (somente leitura). Cada banco é um adaptador; a conta (agência/conta) vem da
 * configuração do adaptador. Devolve só débitos contabilizados, de {@code de} a {@code ate} (inclusive), com a
 * paginação e o fatiamento do período resolvidos por dentro.
 *
 * @throws ExtratoBancarioException falha ao consultar o banco (o chamador registra e tenta no próximo ciclo)
 */
public interface ExtratoBancarioClient {

    List<LancamentoExtrato> listarDebitos(LocalDate de, LocalDate ate);
}
