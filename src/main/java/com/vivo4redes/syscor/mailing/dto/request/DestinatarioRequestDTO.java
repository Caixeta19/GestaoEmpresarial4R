package com.vivo4redes.syscor.mailing.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DestinatarioRequestDTO {
    private Long clienteId;
    private String telefone;
    private List<String> parametros;
}