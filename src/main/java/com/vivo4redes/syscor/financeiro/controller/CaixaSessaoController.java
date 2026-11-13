package com.vivo4redes.syscor.financeiro.controller;

import com.vivo4redes.syscor.financeiro.dto.caixa.*;
import com.vivo4redes.syscor.financeiro.enums.ConferenciaCaixa;
import com.vivo4redes.syscor.financeiro.service.CaixaSessaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Caixa diário da filial. Quem pode o quê: ver {@code CaixaPerfilService}. */
@RestController
@RequestMapping("/financeiro/caixa")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CaixaSessaoController {

    private final CaixaSessaoService caixaSessaoService;

    @PostMapping("/abrir")
    public ResponseEntity<CaixaSessaoResponseDTO> abrir(@Valid @RequestBody AbrirCaixaRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(caixaSessaoService.abrir(dto));
    }

    /** Caixa de hoje da filial: aberto ou não, avisos e se o usuário pode abrir (tela de Venda e gerente). */
    @GetMapping("/situacao")
    public ResponseEntity<SituacaoCaixaResponseDTO> situacao(@RequestParam Long filialId) {
        return ResponseEntity.ok(caixaSessaoService.situacao(filialId));
    }

    /** Dinheiro sugerido para a abertura: o contabilizado do último caixa fechado desse caixa. */
    @GetMapping("/sugestao-abertura")
    public ResponseEntity<SugestaoAberturaResponseDTO> sugestaoAbertura(
            @RequestParam Long filialId, @RequestParam String caixaPdv) {
        return ResponseEntity.ok(caixaSessaoService.sugestaoAbertura(filialId, caixaPdv));
    }

    @GetMapping("/abertos")
    public ResponseEntity<PaginaCaixaResponseDTO> listarAbertos(
            @RequestParam(required = false) Long filialId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) ConferenciaCaixa conferencia,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "data") String ordenar,
            @RequestParam(defaultValue = "desc") String direcao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(caixaSessaoService.listarAbertos(
                filialId, dataInicio, dataFim, conferencia, busca, ordenar, direcao, page, size));
    }

    @GetMapping("/fechados")
    public ResponseEntity<PaginaCaixaResponseDTO> listarFechados(
            @RequestParam(required = false) Long filialId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) ConferenciaCaixa conferencia,
            @RequestParam(required = false) String busca,
            @RequestParam(defaultValue = "data") String ordenar,
            @RequestParam(defaultValue = "desc") String direcao,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(caixaSessaoService.listarFechados(
                filialId, dataInicio, dataFim, conferencia, busca, ordenar, direcao, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CaixaSessaoResponseDTO> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(caixaSessaoService.buscarPorId(id));
    }

    @GetMapping("/{id}/movimento-detalhado")
    public ResponseEntity<List<MovimentoCaixaResponseDTO>> movimentoDetalhado(@PathVariable Long id) {
        return ResponseEntity.ok(caixaSessaoService.movimentoDetalhado(id));
    }

    @PostMapping("/{id}/fechar")
    public ResponseEntity<CaixaSessaoResponseDTO> fechar(
            @PathVariable Long id, @Valid @RequestBody FecharCaixaRequestDTO dto) {
        return ResponseEntity.ok(caixaSessaoService.fechar(id, dto));
    }

    @PostMapping("/{id}/conferir")
    public ResponseEntity<CaixaSessaoResponseDTO> conferir(@PathVariable Long id) {
        return ResponseEntity.ok(caixaSessaoService.conferir(id));
    }

    @PostMapping("/{id}/reabrir")
    public ResponseEntity<CaixaSessaoResponseDTO> reabrir(
            @PathVariable Long id, @RequestBody(required = false) ReabrirCaixaRequestDTO dto) {
        return ResponseEntity.ok(caixaSessaoService.reabrir(id, dto));
    }
}
