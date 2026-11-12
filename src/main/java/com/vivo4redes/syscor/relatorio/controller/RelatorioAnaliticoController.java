package com.vivo4redes.syscor.relatorio.controller;

import com.vivo4redes.syscor.relatorio.dto.CampoRelatorio;
import com.vivo4redes.syscor.relatorio.dto.FiltroRelatorioDTO;
import com.vivo4redes.syscor.relatorio.dto.PaginaRelatorioDTO;
import com.vivo4redes.syscor.relatorio.enums.OpcoesFiltro.AgruparPor;
import com.vivo4redes.syscor.relatorio.enums.OpcoesFiltro.Considerar;
import com.vivo4redes.syscor.relatorio.enums.OpcoesFiltro.Modo;
import com.vivo4redes.syscor.relatorio.enums.OpcoesFiltro.StatusVendaFiltro;
import com.vivo4redes.syscor.relatorio.enums.SegmentoComercial;
import com.vivo4redes.syscor.relatorio.enums.TipoProduto;
import com.vivo4redes.syscor.relatorio.enums.TipoRelatorio;
import com.vivo4redes.syscor.relatorio.service.RelatorioAnaliticoService;
import com.vivo4redes.syscor.venda.enums.StatusAvaliacaoProcedencia;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Relatórios "Venda de Produtos", "Venda de Serviços" e "Vendas" (docs/relatorios_produtos_servicos.md):
 * {@code /relatorios/{produtos|servicos|vendas}/busca|contagem|exportacao|opcoes}. Listas aceitam o parâmetro
 * repetido ou separado por vírgula.
 */
@RestController
@RequestMapping("/relatorios/{tipo}")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RelatorioAnaliticoController {

    private final RelatorioAnaliticoService service;

    @GetMapping("/busca")
    public ResponseEntity<PaginaRelatorioDTO> buscar(
            @PathVariable String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) Considerar considerar,
            @RequestParam(required = false) List<Long> filialIds,
            @RequestParam(required = false) Long vendedorId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StatusAvaliacaoProcedencia statusBko,
            @RequestParam(required = false) StatusVendaFiltro statusVenda,
            @RequestParam(required = false) List<SegmentoComercial> segmentos,
            @RequestParam(required = false) List<TipoProduto> tiposProduto,
            @RequestParam(required = false) Modo modo,
            @RequestParam(required = false) AgruparPor agruparPor,
            @RequestParam(required = false) List<String> campos,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        FiltroRelatorioDTO filtro = new FiltroRelatorioDTO(dataInicio, dataFim, considerar, filialIds, vendedorId,
                clienteId, q, statusBko, statusVenda, segmentos, tiposProduto, modo, agruparPor, campos);
        return ResponseEntity.ok(service.buscar(TipoRelatorio.de(tipo), filtro, page, size));
    }

    /** "N registros encontrados" do rodapé (no sintético, o número de linhas do resumo). */
    @GetMapping("/contagem")
    public ResponseEntity<Map<String, Long>> contar(
            @PathVariable String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) Considerar considerar,
            @RequestParam(required = false) List<Long> filialIds,
            @RequestParam(required = false) Long vendedorId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StatusAvaliacaoProcedencia statusBko,
            @RequestParam(required = false) StatusVendaFiltro statusVenda,
            @RequestParam(required = false) List<SegmentoComercial> segmentos,
            @RequestParam(required = false) List<TipoProduto> tiposProduto,
            @RequestParam(required = false) Modo modo,
            @RequestParam(required = false) AgruparPor agruparPor) {
        FiltroRelatorioDTO filtro = new FiltroRelatorioDTO(dataInicio, dataFim, considerar, filialIds, vendedorId,
                clienteId, q, statusBko, statusVenda, segmentos, tiposProduto, modo, agruparPor, null);
        return ResponseEntity.ok(Map.of("total", service.contar(TipoRelatorio.de(tipo), filtro)));
    }

    /** Excel (.xlsx) só com os campos escolhidos em {@code campos=}; período obrigatório de até 93 dias. */
    @GetMapping("/exportacao")
    public ResponseEntity<byte[]> exportar(
            @PathVariable String tipo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) Considerar considerar,
            @RequestParam(required = false) List<Long> filialIds,
            @RequestParam(required = false) Long vendedorId,
            @RequestParam(required = false) Long clienteId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) StatusAvaliacaoProcedencia statusBko,
            @RequestParam(required = false) StatusVendaFiltro statusVenda,
            @RequestParam(required = false) List<SegmentoComercial> segmentos,
            @RequestParam(required = false) List<TipoProduto> tiposProduto,
            @RequestParam(required = false) Modo modo,
            @RequestParam(required = false) AgruparPor agruparPor,
            @RequestParam(required = false) List<String> campos) {
        FiltroRelatorioDTO filtro = new FiltroRelatorioDTO(dataInicio, dataFim, considerar, filialIds, vendedorId,
                clienteId, q, statusBko, statusVenda, segmentos, tiposProduto, modo, agruparPor, campos);
        RelatorioAnaliticoService.Arquivo arquivo = service.exportar(TipoRelatorio.de(tipo), filtro);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(RelatorioAnaliticoService.CONTENT_TYPE_XLSX))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo.nome()).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(arquivo.conteudo());
    }

    /** Campos escolhíveis (com padrão, travado e "somente excel"), segmentos e tipos de produto do relatório. */
    @GetMapping("/opcoes")
    public ResponseEntity<Map<String, Object>> opcoes(@PathVariable String tipo) {
        TipoRelatorio t = TipoRelatorio.de(tipo);
        return ResponseEntity.ok(Map.of(
                "campos", CampoRelatorio.doRelatorio(t),
                "segmentos", t == TipoRelatorio.SERVICOS
                        ? Arrays.stream(SegmentoComercial.values())
                                .map(s -> Map.of("valor", s.name(), "rotulo", s.rotulo())).toList()
                        : List.of(),
                "tiposProduto", t == TipoRelatorio.PRODUTOS
                        ? Arrays.stream(TipoProduto.values())
                                .map(s -> Map.of("valor", s.name(), "rotulo", s.rotulo())).toList()
                        : List.of()));
    }
}
