package com.vivo4redes.syscor.gestao.controller;

import com.vivo4redes.syscor.gestao.dto.request.ProtocoloDocumentalRequestDTO;
import com.vivo4redes.syscor.gestao.dto.response.ProtocoloDocumentalResponseDTO;
import com.vivo4redes.syscor.gestao.service.ProtocoloDocumentalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/documental/protocolos")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ProtocoloDocumentalController {

    private final ProtocoloDocumentalService protocoloDocumentalService;

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
}