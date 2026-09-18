package com.vivo4redes.syscor.mailing.controller;

import com.vivo4redes.syscor.mailing.dto.request.CriarCampanhaRequestDTO;
import com.vivo4redes.syscor.mailing.dto.response.CampanhaResponseDTO;
import com.vivo4redes.syscor.mailing.service.CampanhaMailingService;
import com.vivo4redes.syscor.mailing.service.ModeloPlanilhaMailingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/mailing/campanhas")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CampanhaMailingController {

    private final CampanhaMailingService campanhaMailingService;
    private final ModeloPlanilhaMailingService modeloPlanilhaMailingService;

    @PostMapping
    public ResponseEntity<CampanhaResponseDTO> criar(@Valid @RequestBody CriarCampanhaRequestDTO dto) {
        var campanha = campanhaMailingService.criar(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(campanha);
    }

    @GetMapping("/modelo-planilha")
    public ResponseEntity<byte[]> baixarModeloPlanilha() {
        byte[] arquivo = modeloPlanilhaMailingService.gerarModelo();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("modelo-mailing.xlsx").build().toString())
                .body(arquivo);
    }

    @PostMapping(value = "/importar-planilha", consumes = "multipart/form-data")
    public ResponseEntity<CampanhaResponseDTO> importarPlanilha(
            @RequestParam("arquivo") MultipartFile arquivo,
            @RequestParam String nome,
            @RequestParam String templateNome,
            @RequestParam(required = false) String templateIdioma) {
        var campanha = campanhaMailingService.criarAPartirDePlanilha(nome, templateNome, templateIdioma, arquivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(campanha);
    }

    @PostMapping("/{id}/disparar")
    public ResponseEntity<CampanhaResponseDTO> disparar(@PathVariable Long id) {
        return ResponseEntity.accepted().body(campanhaMailingService.disparar(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CampanhaResponseDTO> buscar(@PathVariable Long id) {
        return ResponseEntity.ok(campanhaMailingService.buscarComDestinatarios(id));
    }

    @GetMapping
    public ResponseEntity<List<CampanhaResponseDTO>> listar() {
        return ResponseEntity.ok(campanhaMailingService.listar());
    }
}