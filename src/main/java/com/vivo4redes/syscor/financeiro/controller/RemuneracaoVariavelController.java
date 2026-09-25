package com.vivo4redes.syscor.financeiro.controller;

import com.vivo4redes.syscor.financeiro.dto.response.ExecucaoRemuneracaoResponseDTO;
import com.vivo4redes.syscor.financeiro.service.RemuneracaoVariavelService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/financeiro/remuneracao")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class RemuneracaoVariavelController {

    private final RemuneracaoVariavelService remuneracaoVariavelService;

    @PostMapping(value = "/importar-consolidado", consumes = "multipart/form-data")
    public ResponseEntity<ExecucaoRemuneracaoResponseDTO> importarConsolidado(
            @RequestParam("arquivo") MultipartFile arquivo) {
        var resumo = remuneracaoVariavelService.processarConsolidado(arquivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(resumo);
    }

    @GetMapping("/{execucaoId}")
    public ResponseEntity<ExecucaoRemuneracaoResponseDTO> buscarExecucao(@PathVariable Long execucaoId) {
        return ResponseEntity.ok(remuneracaoVariavelService.buscarExecucao(execucaoId));
    }
}