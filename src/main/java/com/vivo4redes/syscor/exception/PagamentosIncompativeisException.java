package com.vivo4redes.syscor.exception;

import com.vivo4redes.syscor.venda.dto.response.PagamentoIncompativelDTO;
import com.vivo4redes.syscor.venda.enums.CondicaoPagamento;
import lombok.Getter;

import java.util.List;

/** Troca de condição com linhas de pagamento que a nova condição não aceita: a resposta lista quais remover. */
@Getter
public class PagamentosIncompativeisException extends BusinessException {

    private final transient List<PagamentoIncompativelDTO> pagamentos;

    public PagamentosIncompativeisException(CondicaoPagamento condicao, List<PagamentoIncompativelDTO> pagamentos) {
        super("Remova os pagamentos incompatíveis com a condição " + condicao + ": "
                + pagamentos.stream().map(p -> p.forma() + " de " + p.valor()).toList() + ".");
        this.pagamentos = pagamentos;
    }
}
