package com.vivo4redes.syscor.estoque.service;

import com.vivo4redes.syscor.estoque.enums.TabelaPreco;
import com.vivo4redes.syscor.util.TextoUtil;
import org.springframework.stereotype.Component;

/**
 * Regra 3: qual tabela da Telefônica vale para a venda. Único lugar dessa decisão — mude aqui.
 * <p>
 * RENOVA quando o serviço é troca de aparelho de cliente que já tem linha; REGULAR nos demais casos.
 * Hoje a troca é reconhecida pelo {@code tipoServico} do item contendo "troca" ou "renova"
 * (sem acento, sem diferenciar maiúsculas).
 */
@Component
public class ResolvedorTabela {

    public TabelaPreco resolver(String tipoServico) {
        String tipo = TextoUtil.normalizar(tipoServico);
        return tipo.contains("troca") || tipo.contains("renova") ? TabelaPreco.RENOVA : TabelaPreco.REGULAR;
    }
}
