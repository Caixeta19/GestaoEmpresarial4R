package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;
import com.vivo4redes.syscor.estoque.model.InventarioItem;
import com.vivo4redes.syscor.estoque.service.diagnostico.CausaProvavelIa;

import java.time.Instant;
import java.time.LocalDate;

/** Um serial do inventário, com as três fontes (IQ09, sistema, bipagem), o resultado e o diagnóstico da IA. */
public record InventarioItemResponseDTO(
        Long id,
        String serial,
        String serialNormalizado,
        String materialSap,
        String descricao,
        String centroSap,
        String depositoSap,
        String statusSistemaSap,
        String statusUsuarioSap,
        LocalDate modificadoEmSap,
        String modificadoPorSap,
        boolean esperadoIq09,
        boolean esperadoSistema,
        Long itemEstoqueId,
        String statusEstoque,
        String filialEstoque,
        Instant bipadoEm,
        String bipadoPor,
        String justificativa,
        ResultadoConciliacaoInventario resultado,
        String observacao,
        String causaProvavel,
        String causaProvavelRotulo,
        String confiancaIa,
        String acaoSugerida
) {
    public static InventarioItemResponseDTO from(InventarioItem i) {
        return new InventarioItemResponseDTO(
                i.getId(), i.getSerialSap(), i.getSerialNormalizado(), i.getMaterialSap(), i.getDenominacaoSap(),
                i.getCentroSap(), i.getDepositoSap(), i.getStatusSap(), i.getStatusUsuarioSap(),
                i.getModificadoEmSap(), i.getModificadoPorSap(),
                i.isEsperadoIq09(), i.isEsperadoSistema(), i.getItemEstoqueId(), i.getStatusSistema(), i.getFilialSistema(),
                i.getBipadoEm(), i.getBipadoPor() == null ? null : i.getBipadoPor().getNome(),
                i.getJustificativa(), i.getResultado(), i.getObservacao(),
                i.getCausaProvavel(), CausaProvavelIa.rotuloDe(i.getCausaProvavel()), i.getConfiancaIa(), i.getAcaoSugerida());
    }
}
