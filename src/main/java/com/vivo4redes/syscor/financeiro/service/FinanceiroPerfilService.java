package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.exception.FinanceiroException;
import com.vivo4redes.syscor.venda.model.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Matriz de perfis de contas a pagar, reaproveitando os cargos do caixa ({@link CaixaPerfilService}) sem mexer nela:
 * <ul>
 *   <li>cadastrar, editar, cancelar, baixa manual, reabrir, vincular e sincronizar: financeiro e admin;</li>
 *   <li>consultar (lista, resumo, comprovante): financeiro, admin, auditoria e gerente de área;</li>
 *   <li>gerente de loja: sem acesso (403 FINANCEIRO_SEM_PERMISSAO).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class FinanceiroPerfilService {

    private final CaixaPerfilService caixaPerfil;

    public boolean podeConsultar(Usuario u) {
        return podeGerenciar(u) || caixaPerfil.ehAuditoria(u) || caixaPerfil.ehGerenteArea(u);
    }

    public boolean podeGerenciar(Usuario u) {
        return caixaPerfil.ehAdmin(u) || caixaPerfil.ehFinanceiro(u);
    }

    public void exigirConsulta(Usuario u) {
        if (!podeConsultar(u)) throw FinanceiroException.semPermissao();
    }

    public void exigirGerenciar(Usuario u) {
        if (!podeGerenciar(u)) throw FinanceiroException.semPermissao();
    }
}
