package com.vivo4redes.syscor.venda.controller;

import com.vivo4redes.syscor.venda.dto.ResumoCarrinhoDTO;
import com.vivo4redes.syscor.venda.dto.request.AvaliacaoProcedenciaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.DadosIniciaisVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.FinalizarVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.ItemVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.PagamentoVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.StatusVendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.request.VendaRequestDTO;
import com.vivo4redes.syscor.venda.dto.response.VendaResponseDTO;
import com.vivo4redes.syscor.venda.model.Venda;
import com.vivo4redes.syscor.venda.service.VendaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.vivo4redes.syscor.venda.dto.VendaFiltroDTO;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;

@RestController
@RequestMapping("/vendas")
public class VendaController {

    private final VendaService vendaService;

    public VendaController(VendaService vendaService) {
        this.vendaService = vendaService;
    }

    @PostMapping
    public ResponseEntity<VendaResponseDTO> abrirCarrinho(@Valid @RequestBody VendaRequestDTO dto) {
        var venda = vendaService.abrirCarrinho(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(responder(venda));
    }

    @GetMapping("/{id}")
    public ResponseEntity<VendaResponseDTO> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(responder(vendaService.buscarPorId(id)));
    }

    @GetMapping
    public ResponseEntity<List<VendaResponseDTO>> listar(
            @RequestParam(required = false) Long filialId,
            @RequestParam(required = false) Long vendedorId,
            @RequestParam(required = false) Long numeroVenda,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataInicio,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dataFim,
            @RequestParam(required = false) String cliente,
            @RequestParam(required = false) String numeroAcesso,
            @RequestParam(required = false) String serialProdutoVivo,
            @RequestParam(required = false) String serialSimcard,
            @RequestParam(required = false) String modeloAcessorio) {

        var filtro = new VendaFiltroDTO(filialId, vendedorId, numeroVenda, dataInicio, dataFim,
                cliente, numeroAcesso, serialProdutoVivo, serialSimcard, modeloAcessorio);

        var vendas = vendaService.buscar(filtro).stream().map(this::responder).toList();
        return ResponseEntity.ok(vendas);
    }

    @PutMapping("/{id}")
    public ResponseEntity<VendaResponseDTO> atualizarDadosIniciais(
            @PathVariable Long id, @Valid @RequestBody DadosIniciaisVendaRequestDTO dto) {
        var venda = vendaService.atualizarDadosIniciais(id, dto);
        return ResponseEntity.ok(responder(venda));
    }

    @PostMapping("/{id}/itens")
    public ResponseEntity<VendaResponseDTO> adicionarItem(
            @PathVariable Long id, @Valid @RequestBody ItemVendaRequestDTO dto) {
        var venda = vendaService.adicionarItem(id, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(responder(venda));
    }

    @DeleteMapping("/{id}/itens/{itemId}")
    public ResponseEntity<VendaResponseDTO> removerItem(@PathVariable Long id, @PathVariable Long itemId) {
        var venda = vendaService.removerItem(id, itemId);
        return ResponseEntity.ok(responder(venda));
    }

    @PostMapping("/{id}/pagamentos")
    public ResponseEntity<VendaResponseDTO> adicionarPagamento(
            @PathVariable Long id, @Valid @RequestBody PagamentoVendaRequestDTO dto) {
        var venda = vendaService.adicionarPagamento(id, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(responder(venda));
    }

    @DeleteMapping("/{id}/pagamentos/{pagamentoId}")
    public ResponseEntity<VendaResponseDTO> removerPagamento(
            @PathVariable Long id, @PathVariable Long pagamentoId) {
        var venda = vendaService.removerPagamento(id, pagamentoId);
        return ResponseEntity.ok(responder(venda));
    }

    @GetMapping("/{id}/resumo")
    public ResponseEntity<ResumoCarrinhoDTO> obterResumo(@PathVariable Long id) {
        return ResponseEntity.ok(vendaService.obterResumo(id));
    }

    @PatchMapping("/{id}/finalizar")
    public ResponseEntity<VendaResponseDTO> finalizar(
            @PathVariable Long id, @Valid @RequestBody FinalizarVendaRequestDTO dto) {
        var venda = vendaService.finalizar(id, dto);
        return ResponseEntity.ok(responder(venda));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<VendaResponseDTO> avancarStatus(
            @PathVariable Long id, @Valid @RequestBody StatusVendaRequestDTO dto) {
        var venda = vendaService.avancarStatus(id, dto);
        return ResponseEntity.ok(responder(venda));
    }

    @PatchMapping("/{id}/avaliacao-procedencia")
    public ResponseEntity<VendaResponseDTO> avaliarProcedencia(
            @PathVariable Long id, @Valid @RequestBody AvaliacaoProcedenciaRequestDTO dto) {
        var venda = vendaService.avaliarProcedencia(id, dto.resultado());
        return ResponseEntity.ok(responder(venda));
    }

    private VendaResponseDTO responder(Venda venda) {
        return VendaResponseDTO.from(venda, vendaService.listarPagamentos(venda.getId()));
    }
}