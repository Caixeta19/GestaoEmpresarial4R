package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.enums.ResultadoConciliacaoInventario;
import com.vivo4redes.syscor.estoque.model.InventarioItem;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InventarioItemResponseDTO {
    private Long id;
    private String materialSap;
    private String denominacaoSap;
    private String serialSap;
    private String centroSap;
    private String depositoSap;
    private String statusSap;
    private ResultadoConciliacaoInventario resultado;
    private String observacao;
    private Long itemEstoqueId;

    public static InventarioItemResponseDTO from(InventarioItem i) {
        return InventarioItemResponseDTO.builder()
                .id(i.getId())
                .materialSap(i.getMaterialSap())
                .denominacaoSap(i.getDenominacaoSap())
                .serialSap(i.getSerialSap())
                .centroSap(i.getCentroSap())
                .depositoSap(i.getDepositoSap())
                .statusSap(i.getStatusSap())
                .resultado(i.getResultado())
                .observacao(i.getObservacao())
                .itemEstoqueId(i.getItemEstoqueId())
                .build();
    }
}