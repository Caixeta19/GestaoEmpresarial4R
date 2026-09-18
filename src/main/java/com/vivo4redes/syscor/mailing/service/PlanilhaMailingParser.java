package com.vivo4redes.syscor.mailing.service;

import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.mailing.dto.request.DestinatarioRequestDTO;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.*;

@Component
public class PlanilhaMailingParser {

    public List<DestinatarioRequestDTO> parsear(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw new NegocioException("Envie a planilha de destinatários preenchida (.xlsx).");
        }

        try (InputStream is = arquivo.getInputStream(); Workbook wb = WorkbookFactory.create(is)) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            Row cabecalho = sheet.getRow(sheet.getFirstRowNum());
            if (cabecalho == null) {
                throw new NegocioException("A planilha enviada está vazia.");
            }

            Integer idxTelefone = null, idxClienteId = null;
            Map<Integer, Integer> colunaParametroPorOrdem = new TreeMap<>();

            for (int c = 0; c < cabecalho.getLastCellNum(); c++) {
                String nome = normalizar(formatter.formatCellValue(cabecalho.getCell(c)));
                if (nome.equals("telefone")) idxTelefone = c;
                else if (nome.equals("clienteid") || nome.equals("cliente_id") || nome.equals("cliente id")) idxClienteId = c;
                else if (nome.startsWith("parametro")) {
                    String numero = nome.replaceAll("\\D", "");
                    if (!numero.isBlank()) {
                        colunaParametroPorOrdem.put(Integer.parseInt(numero), c);
                    }
                }
            }

            if (idxTelefone == null && idxClienteId == null) {
                throw new NegocioException("A planilha precisa ter a coluna 'Telefone' e/ou 'ClienteId'.");
            }

            List<DestinatarioRequestDTO> destinatarios = new ArrayList<>();
            for (int r = sheet.getFirstRowNum() + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;

                String telefone = idxTelefone == null ? null : valorTexto(row, idxTelefone, formatter);
                String clienteIdTexto = idxClienteId == null ? null : valorTexto(row, idxClienteId, formatter);

                boolean linhaVazia = (telefone == null || telefone.isBlank()) && (clienteIdTexto == null || clienteIdTexto.isBlank());
                if (linhaVazia) continue;

                Long clienteId = null;
                if (clienteIdTexto != null && !clienteIdTexto.isBlank()) {
                    try {
                        clienteId = Long.valueOf(clienteIdTexto.trim());
                    } catch (NumberFormatException e) {
                        throw new NegocioException("Linha " + (r + 1) + ": ClienteId '" + clienteIdTexto + "' não é um número válido.");
                    }
                }

                List<String> parametros = new ArrayList<>();
                for (Integer coluna : colunaParametroPorOrdem.values()) {
                    parametros.add(valorTexto(row, coluna, formatter));
                }

                destinatarios.add(DestinatarioRequestDTO.builder()
                        .telefone((telefone == null || telefone.isBlank()) ? null : telefone.trim())
                        .clienteId(clienteId)
                        .parametros(parametros)
                        .build());
            }

            if (destinatarios.isEmpty()) {
                throw new NegocioException("Nenhum destinatário válido foi encontrado na planilha.");
            }
            return destinatarios;
        } catch (IOException e) {
            throw new NegocioException("Não foi possível ler a planilha enviada: " + e.getMessage());
        }
    }

    private String valorTexto(Row row, int idx, DataFormatter formatter) {
        Cell cell = row.getCell(idx);
        return cell == null ? "" : formatter.formatCellValue(cell).trim();
    }

    private String normalizar(String texto) {
        if (texto == null) return "";
        String semAcento = Normalizer.normalize(texto, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return semAcento.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }
}