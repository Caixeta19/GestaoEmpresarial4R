package com.vivo4redes.syscor.estoque.dto.response;

import com.vivo4redes.syscor.estoque.enums.StatusInventario;
import com.vivo4redes.syscor.estoque.model.InventarioExecucao;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

/**
 * Execução de inventário: cabeçalho, resumo da importação, contadores e diagnóstico. No detalhe ({@code GET /{id}})
 * traz também a página de itens e as últimas bipagens; na listagem esses campos vêm nulos.
 */
public record InventarioResumoResponseDTO(
        Long execucaoId,
        Long filialId,
        String filialNome,
        String centroSap,
        StatusInventario status,
        String nomeArquivo,
        Instant abertoEm,
        String abertoPor,
        Instant fechadoEm,
        String fechadoPor,
        int totalLinhasSap,
        int linhasIgnoradasOutroCentro,
        int linhasSemSerial,
        List<String> centrosSemFilial,
        TotaisInventarioDTO totais,
        String resumoIa,
        Instant diagnosticadoEm,
        PaginaItens itens,
        List<InventarioItemResponseDTO> ultimasBipagens
) {

    public record PaginaItens(List<InventarioItemResponseDTO> conteudo, int pagina, int tamanho,
                              long totalItens, int totalPaginas) {
    }

    public static InventarioResumoResponseDTO from(InventarioExecucao e, TotaisInventarioDTO totais,
                                                   PaginaItens itens, List<InventarioItemResponseDTO> ultimasBipagens) {
        var filial = e.getFilial();
        List<String> centros = e.getCentrosSemFilial() == null || e.getCentrosSemFilial().isBlank()
                ? List.of()
                : Arrays.stream(e.getCentrosSemFilial().split(",")).map(String::trim).filter(c -> !c.isEmpty()).toList();
        return new InventarioResumoResponseDTO(
                e.getId(),
                filial == null ? null : filial.getId(),
                filial == null ? null : filial.getNome(),
                filial == null ? null : filial.getCentroSap(),
                e.getStatus(),
                e.getNomeArquivo(),
                e.getCriadoEm(),
                e.getAbertoPor() != null ? e.getAbertoPor().getNome() : e.getExecutadoPor(),
                e.getFechadoEm(),
                e.getFechadoPor() == null ? null : e.getFechadoPor().getNome(),
                e.getTotalLinhasSap(),
                e.getLinhasIgnoradasOutroCentro(),
                e.getLinhasSemSerial(),
                centros,
                totais,
                e.getResumoIa(),
                e.getDiagnosticadoEm(),
                itens,
                ultimasBipagens);
    }
}
