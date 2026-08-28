package com.vivo4redes.syscor.controller;
import com.vivo4redes.syscor.dto.*;
import com.vivo4redes.syscor.dto.request.AvaliacaoProcedenciaRequestDTO;
import com.vivo4redes.syscor.dto.request.DadosIniciaisVendaRequestDTO;
import com.vivo4redes.syscor.dto.request.FinalizarVendaRequestDTO;
import com.vivo4redes.syscor.dto.request.ItemVendaRequestDTO;
import com.vivo4redes.syscor.dto.request.StatusVendaRequestDTO;
import com.vivo4redes.syscor.dto.request.VendaRequestDTO;
import com.vivo4redes.syscor.dto.response.VendaResponseDTO;
import com.vivo4redes.syscor.service.VendaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Endpoints pensados para a tela de registro de venda com abas por
 * categoria (Produto Vivo / Serviço Vivo / Recarga). O front chama
 * GET /{id}/resumo após cada adição/remoção de item para atualizar os
 * badges de contagem das abas, sem recarregar a venda inteira.
 */
@RestController
@RequestMapping("/vendas")
public class VendaController {

    private final VendaService vendaService;

    public VendaController(VendaService vendaService) {
        this.vendaService = vendaService;
    }

    /** Abre um novo carrinho (status ABERTA) para o cliente informado. */
    @PostMapping
    public ResponseEntity<VendaResponseDTO> abrirCarrinho(@Valid @RequestBody VendaRequestDTO dto) {
        var venda = vendaService.abrirCarrinho(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(VendaResponseDTO.from(venda));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(VendaResponseDTO.from(vendaService.buscarPorId(id)));
    }

    /** Listagem geral de vendas (mais recentes primeiro). */
    @GetMapping
    public ResponseEntity<List<VendaResponseDTO>> listar() {
        var vendas = vendaService.listarTodas().stream().map(VendaResponseDTO::from).toList();
        return ResponseEntity.ok(vendas);
    }

    /** Edita os campos da tela "Início" de uma venda já aberta — exige reautenticação. */
    @PutMapping("/{id}")
    public ResponseEntity<VendaResponseDTO> atualizarDadosIniciais(
            @PathVariable Long id, @Valid @RequestBody DadosIniciaisVendaRequestDTO dto) {
        var venda = vendaService.atualizarDadosIniciais(id, dto);
        return ResponseEntity.ok(VendaResponseDTO.from(venda));
    }

    /** Adiciona um item em uma das três abas (categoria vem no corpo). */
    @PostMapping("/{id}/itens")
    public ResponseEntity<VendaResponseDTO> adicionarItem(
            @PathVariable Long id, @Valid @RequestBody ItemVendaRequestDTO dto) {
        var venda = vendaService.adicionarItem(id, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(VendaResponseDTO.from(venda));
    }

    @DeleteMapping("/{id}/itens/{itemId}")
    public ResponseEntity<VendaResponseDTO> removerItem(@PathVariable Long id, @PathVariable Long itemId) {
        var venda = vendaService.removerItem(id, itemId);
        return ResponseEntity.ok(VendaResponseDTO.from(venda));
    }

    /** Alimenta os badges "Produto Vivo (3)", "Serviço Vivo (0)", "Recarga (0)". */
    @GetMapping("/{id}/resumo")
    public ResponseEntity<ResumoCarrinhoDTO> obterResumo(@PathVariable Long id) {
        return ResponseEntity.ok(vendaService.obterResumo(id));
    }

    /** Encerra o carrinho: ABERTA -> PENDENTE. Exige ao menos 1 item e reautenticação do vendedor. */
    @PatchMapping("/{id}/finalizar")
    public ResponseEntity<VendaResponseDTO> finalizar(
            @PathVariable Long id, @Valid @RequestBody FinalizarVendaRequestDTO dto) {
        var venda = vendaService.finalizar(id, dto);
        return ResponseEntity.ok(VendaResponseDTO.from(venda));
    }

    /** PENDENTE -> APROVADA -> CONCLUIDA, ou -> CANCELADA a partir de qualquer estado não-terminal. */
    @PatchMapping("/{id}/status")
    public ResponseEntity<VendaResponseDTO> avancarStatus(
            @PathVariable Long id, @Valid @RequestBody StatusVendaRequestDTO dto) {
        var venda = vendaService.avancarStatus(id, dto);
        return ResponseEntity.ok(VendaResponseDTO.from(venda));
    }

    /** US-303: avaliação de procedência — improcedente cancela a venda automaticamente. */
    @PatchMapping("/{id}/avaliacao-procedencia")
    public ResponseEntity<VendaResponseDTO> avaliarProcedencia(
            @PathVariable Long id, @Valid @RequestBody AvaliacaoProcedenciaRequestDTO dto) {
        var venda = vendaService.avaliarProcedencia(id, dto.resultado());
        return ResponseEntity.ok(VendaResponseDTO.from(venda));
    }
}