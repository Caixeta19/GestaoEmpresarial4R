package com.vivo4redes.syscor.gestao.controller;

import com.vivo4redes.syscor.gestao.dto.request.FiltroDocumentalDTO;
import com.vivo4redes.syscor.gestao.dto.request.ProtocoloDocumentalRequestDTO;
import com.vivo4redes.syscor.gestao.dto.response.ProtocoloDocumentalResponseDTO;
import com.vivo4redes.syscor.gestao.dto.response.ResultadoImportacaoProtocolosDTO;
import com.vivo4redes.syscor.gestao.enums.TipoPlano;
import com.vivo4redes.syscor.gestao.service.ExportacaoDocumentalService;
import com.vivo4redes.syscor.gestao.service.ImportacaoProtocolosService;
import com.vivo4redes.syscor.gestao.service.ProtocoloDocumentalService;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/documental/protocolos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProtocoloDocumentalController {

    private static final int TAMANHO_MAXIMO_PAGINA = 200;

    private final ProtocoloDocumentalService protocoloDocumentalService;
    private final ImportacaoProtocolosService importacaoService;
    private final ExportacaoDocumentalService exportacaoService;

    @PutMapping("/{vendaId}")
    public ResponseEntity<ProtocoloDocumentalResponseDTO> salvar(
            @PathVariable Long vendaId, @Valid @RequestBody ProtocoloDocumentalRequestDTO dto) {
        return ResponseEntity.ok(protocoloDocumentalService.salvar(vendaId, dto));
    }

    @GetMapping("/{vendaId}")
    public ResponseEntity<ProtocoloDocumentalResponseDTO> buscarPorVenda(@PathVariable Long vendaId) {
        return ResponseEntity.ok(protocoloDocumentalService.buscarPorVenda(vendaId));
    }

    @GetMapping
    public ResponseEntity<List<ProtocoloDocumentalResponseDTO>> buscarPorNumeroProtocoloGed(
            @RequestParam String numeroProtocoloGed) {
        return ResponseEntity.ok(protocoloDocumentalService.buscarPorNumeroProtocoloGed(numeroProtocoloGed));
    }

    /** Vendas lançadas (com ou sem protocolo), paginadas. Listas aceitam o parâmetro repetido ou separado por vírgula. */
    @GetMapping("/busca")
    public ResponseEntity<PagedModel<ProtocoloDocumentalResponseDTO>> buscar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) List<Long> filialIds,
            @RequestParam(required = false) Long vendedorId,
            @RequestParam(required = false) Long numeroVenda,
            @RequestParam(required = false) String numeroAcesso,
            @RequestParam(required = false) String numeroProtocoloGed,
            @RequestParam(required = false) String liderEquipe,
            @RequestParam(required = false) StatusAvaliacaoProcedencia statusBko,
            @RequestParam(required = false) List<TipoPlano> tiposPlano,
            @RequestParam(required = false) List<String> servicos,
            @RequestParam(defaultValue = "false") boolean incluirCanceladas,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        FiltroDocumentalDTO filtro = new FiltroDocumentalDTO(dataInicio, dataFim, filialIds, vendedorId, numeroVenda,
                numeroAcesso, numeroProtocoloGed, liderEquipe, statusBko, tiposPlano, servicos, incluirCanceladas);
        PageRequest pagina = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), TAMANHO_MAXIMO_PAGINA));
        return ResponseEntity.ok(new PagedModel<>(protocoloDocumentalService.buscar(filtro, pagina)));
    }

    @PostMapping(value = "/importacao", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResultadoImportacaoProtocolosDTO> importar(@RequestParam("arquivo") MultipartFile arquivo) {
        return ResponseEntity.ok(importacaoService.importar(arquivo));
    }

    /**
     * gestao_documental.csv no layout do sistema atual (";", Windows-1252, 55 colunas), com os mesmos filtros da
     * busca; período obrigatório de até 93 dias.
     */
    @GetMapping("/exportacao")
    public ResponseEntity<byte[]> exportar(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) List<Long> filialIds,
            @RequestParam(required = false) Long vendedorId,
            @RequestParam(required = false) Long numeroVenda,
            @RequestParam(required = false) String numeroAcesso,
            @RequestParam(required = false) String numeroProtocoloGed,
            @RequestParam(required = false) String liderEquipe,
            @RequestParam(required = false) StatusAvaliacaoProcedencia statusBko,
            @RequestParam(required = false) List<TipoPlano> tiposPlano,
            @RequestParam(required = false) List<String> servicos,
            @RequestParam(defaultValue = "false") boolean incluirCanceladas) {
        FiltroDocumentalDTO filtro = new FiltroDocumentalDTO(dataInicio, dataFim, filialIds, vendedorId, numeroVenda,
                numeroAcesso, numeroProtocoloGed, liderEquipe, statusBko, tiposPlano, servicos, incluirCanceladas);
        ExportacaoDocumentalService.Arquivo arquivo = exportacaoService.exportar(filtro);
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", ExportacaoDocumentalService.CHARSET))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo.nome()).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(arquivo.conteudo());
    }
}
