package com.vivo4redes.syscor.financeiro.controller;

import com.vivo4redes.syscor.financeiro.dto.request.BaixaManualRequestDTO;
import com.vivo4redes.syscor.financeiro.dto.request.CancelarContaRequestDTO;
import com.vivo4redes.syscor.financeiro.dto.request.ContaPagarRequestDTO;
import com.vivo4redes.syscor.financeiro.dto.request.ReabrirContaRequestDTO;
import com.vivo4redes.syscor.financeiro.dto.response.ContaPagarDetalheDTO;
import com.vivo4redes.syscor.financeiro.dto.response.PermissoesContasPagarDTO;
import com.vivo4redes.syscor.financeiro.dto.response.ResumoContasPagarDTO;
import com.vivo4redes.syscor.financeiro.dto.response.TituloPagarDTO;
import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.service.ContaPagarService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Contas a pagar. Quem pode o quê: ver {@code FinanceiroPerfilService}; o service confere em cada ação. */
@RestController
@RequestMapping("/financeiro/contas-pagar")
@RequiredArgsConstructor
public class ContaPagarController {

    private final ContaPagarService contaPagarService;

    @PostMapping
    public ResponseEntity<TituloPagarDTO> cadastrar(@Valid @RequestBody ContaPagarRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(contaPagarService.cadastrar(dto));
    }

    /** Mantém o contrato da lista; os filtros são opcionais. Sem paginação na v1. */
    @GetMapping
    public List<TituloPagarDTO> listar(
            @RequestParam(required = false) StatusTitulo status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoDe,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimentoAte,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long filialId) {
        return contaPagarService.listar(status, vencimentoDe, vencimentoAte, busca, filialId);
    }

    @GetMapping("/resumo")
    public ResumoContasPagarDTO resumo() {
        return contaPagarService.resumo();
    }

    @GetMapping("/permissoes")
    public PermissoesContasPagarDTO permissoes() {
        return contaPagarService.permissoes();
    }

    @GetMapping("/{id}")
    public ContaPagarDetalheDTO detalhe(@PathVariable Long id) {
        return contaPagarService.detalhe(id);
    }

    @PutMapping("/{id}")
    public TituloPagarDTO editar(@PathVariable Long id, @Valid @RequestBody ContaPagarRequestDTO dto) {
        return contaPagarService.editar(id, dto);
    }

    @PostMapping("/{id}/cancelar")
    public TituloPagarDTO cancelar(@PathVariable Long id, @Valid @RequestBody CancelarContaRequestDTO dto) {
        return contaPagarService.cancelar(id, dto);
    }

    @PostMapping("/{id}/baixa-manual")
    public TituloPagarDTO baixaManual(@PathVariable Long id, @Valid @RequestBody BaixaManualRequestDTO dto) {
        return contaPagarService.baixaManual(id, dto);
    }

    @PostMapping("/{id}/reabrir")
    public TituloPagarDTO reabrir(@PathVariable Long id, @Valid @RequestBody ReabrirContaRequestDTO dto) {
        return contaPagarService.reabrir(id, dto);
    }
}
