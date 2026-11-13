package com.vivo4redes.syscor.financeiro.dto.caixa;

/**
 * O que o usuário logado pode fazer no caixa, para o front mostrar ou esconder botões. Quem pode abrir e fechar numa
 * filial específica vem de {@code situacao.podeAbrir}; o backend sempre confere de novo em cada ação.
 */
public record PermissoesCaixaResponseDTO(
        /** Pode conferir e reabrir caixas (auditoria, financeiro e admin). */
        boolean conferirReabrir,
        /** Vê e age em todas as filiais (admin, auditoria e financeiro); o gerente fica só na própria. */
        boolean todasAsFiliais,
        /** Filial do cadastro do usuário, quando casa com exatamente uma filial (para o aviso "caixa de hoje não aberto"). */
        Long filialId
) {
}
