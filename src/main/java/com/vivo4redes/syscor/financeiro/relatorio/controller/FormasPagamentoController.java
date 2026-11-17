package com.vivo4redes.syscor.financeiro.relatorio.controller;

import com.vivo4redes.syscor.financeiro.relatorio.dto.AgruparPor;
import com.vivo4redes.syscor.financeiro.relatorio.dto.FiltroFormasPagamentoDTO;
import com.vivo4redes.syscor.financeiro.relatorio.dto.OpcoesFormasPagamentoDTO;
import com.vivo4redes.syscor.financeiro.relatorio.dto.PermissoesFormasPagamentoDTO;
import com.vivo4redes.syscor.financeiro.relatorio.dto.RelatorioFormasPagamentoDTO;
import com.vivo4redes.syscor.financeiro.relatorio.service.FormasPagamentoService;
import com.vivo4redes.syscor.venda.enums.FormaPagamento;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/** Relatório de Formas de Pagamento (Financeiro › Movimento). Perfis e escopo: ver {@link FormasPagamentoService}. */
@RestController
@RequestMapping("/financeiro/movimento/formas-pagamento")
@RequiredArgsConstructor
public class FormasPagamentoController {

    private final FormasPagamentoService service;

    @GetMapping
    public RelatorioFormasPagamentoDTO relatorio(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(defaultValue = "FILIAL") AgruparPor agruparPor,
            @RequestParam(required = false) List<Long> filialIds,
            @RequestParam(required = false) List<FormaPagamento> formas) {
        return service.relatorio(new FiltroFormasPagamentoDTO(dataInicio, dataFim, agruparPor, filialIds, formas));
    }

    @GetMapping("/opcoes")
    public OpcoesFormasPagamentoDTO opcoes() {
        return service.opcoes();
    }

    @GetMapping("/permissoes")
    public PermissoesFormasPagamentoDTO permissoes() {
        return service.permissoes();
    }

    /**
     * Arquivo gerado na hora. O Content-Type é fixado na resposta (não em {@code produces}) para o erro continuar
     * saindo em JSON; o front baixa com {@code fetch} + Bearer e lê o nome em {@code Content-Disposition}.
     */
    @GetMapping("/exportar")
    public ResponseEntity<byte[]> exportar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(defaultValue = "FILIAL") AgruparPor agruparPor,
            @RequestParam(required = false) List<Long> filialIds,
            @RequestParam(required = false) List<FormaPagamento> formas,
            @RequestParam(defaultValue = "xlsx") String formato) {
        FormasPagamentoService.Arquivo arquivo = service.exportar(
                new FiltroFormasPagamentoDTO(dataInicio, dataFim, agruparPor, filialIds, formas), formato);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(arquivo.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(arquivo.nome()).build().toString())
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(arquivo.conteudo());
    }
}
