package com.vivo4redes.syscor.exception;

import com.vivo4redes.syscor.venda.enums.FormaPagamento;

import java.math.BigDecimal;
import java.util.List;

/**
 * O caixa só fecha com diferença zero em todas as formas (422 {@code CAIXA_COM_DIFERENCA}). Para o gerente da loja o corpo
 * traz só QUAIS formas divergem (sem o valor, que revelaria o esperado) e quantas tentativas restam; para quem pode fechar
 * com divergência (e falta a justificativa) traz também a diferença de cada forma.
 */
public class CaixaComDiferencaException extends BusinessException {

    /** {@code diferenca} é null quando o valor não pode ser mostrado a quem fechou. */
    public record Forma(FormaPagamento forma, BigDecimal diferenca) {
    }

    private final List<Forma> formas;
    private final Integer tentativasRestantes;

    public CaixaComDiferencaException(String mensagem, List<Forma> formas, Integer tentativasRestantes) {
        super(mensagem);
        this.formas = formas;
        this.tentativasRestantes = tentativasRestantes;
    }

    public List<Forma> getFormas() {
        return formas;
    }

    public Integer getTentativasRestantes() {
        return tentativasRestantes;
    }
}
