package com.vivo4redes.syscor.financeiro.controller;

import com.vivo4redes.syscor.financeiro.dto.caixa.EstornoRequestDTO;
import com.vivo4redes.syscor.financeiro.dto.caixa.LancamentoCaixaResponseDTO;
import com.vivo4redes.syscor.financeiro.dto.caixa.LancamentoRequestDTO;
import com.vivo4redes.syscor.financeiro.service.CaixaLancamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Sangria, suprimento e estorno de dinheiro no caixa aberto. 201 ao criar; 200 quando a chave de idempotência já existia. */
@RestController
@RequestMapping("/financeiro/caixa/{caixaId}/lancamentos")
@RequiredArgsConstructor
public class CaixaLancamentoController {

    private final CaixaLancamentoService lancamentoService;

    @PostMapping
    public ResponseEntity<LancamentoCaixaResponseDTO> lancar(
            @PathVariable Long caixaId, @Valid @RequestBody LancamentoRequestDTO dto) {
        return resposta(lancamentoService.lancar(caixaId, dto));
    }

    @GetMapping
    public ResponseEntity<List<LancamentoCaixaResponseDTO>> listar(@PathVariable Long caixaId) {
        return ResponseEntity.ok(lancamentoService.listar(caixaId));
    }

    @PostMapping("/{lancamentoId}/estornar")
    public ResponseEntity<LancamentoCaixaResponseDTO> estornar(
            @PathVariable Long caixaId, @PathVariable Long lancamentoId, @Valid @RequestBody EstornoRequestDTO dto) {
        return resposta(lancamentoService.estornar(caixaId, lancamentoId, dto));
    }

    private static ResponseEntity<LancamentoCaixaResponseDTO> resposta(CaixaLancamentoService.Resultado r) {
        return ResponseEntity.status(r.criado() ? HttpStatus.CREATED : HttpStatus.OK).body(r.lancamento());
    }
}
