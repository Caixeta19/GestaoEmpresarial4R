package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.enums.GrupoPlano;
import com.vivo4redes.syscor.estoque.model.PlanoVivo;

/** Item do combo "Plano Ativo / Linha" (GET /produtos/precos/planos). */
public record PlanoVivoResponseDTO(String nomePlano, GrupoPlano grupoPlano, String sistema, boolean disponivelAlta) {

    public static PlanoVivoResponseDTO from(PlanoVivo p) {
        return new PlanoVivoResponseDTO(p.getNomePlano(), p.getGrupoPlano(), p.getSistema(), p.isDisponivelAlta());
    }
}
