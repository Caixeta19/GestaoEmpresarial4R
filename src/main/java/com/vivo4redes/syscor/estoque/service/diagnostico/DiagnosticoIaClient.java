package com.vivo4redes.syscor.estoque.service.diagnostico;

import java.util.List;

/**
 * Porta para o modelo que diagnostica as divergências. A implementação real chama a API do Claude; os testes usam uma
 * falsa (nenhuma chamada real).
 */
public interface DiagnosticoIaClient {

    /** false sem chave de API: o inventário funciona e o diagnóstico fica indisponível. */
    boolean disponivel();

    /**
     * Um lote (até {@code syscor.ia.lote} itens).
     *
     * @throws RespostaIaInvalidaException resposta fora do formato ou recusada (o serviço tenta de novo)
     * @throws com.vivo4redes.syscor.exception.DiagnosticoIaException falha da API (sem nova tentativa)
     */
    RespostaDiagnosticoIa diagnosticar(List<ItemParaDiagnostico> itens);
}
