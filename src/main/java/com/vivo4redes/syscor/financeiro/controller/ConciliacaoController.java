package com.vivo4redes.syscor.financeiro.controller;

import com.vivo4redes.syscor.financeiro.dto.response.TituloPagarDTO;
import com.vivo4redes.syscor.financeiro.dto.response.WebhookLogDTO;
import com.vivo4redes.syscor.financeiro.enums.StatusWebhook;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import com.vivo4redes.syscor.financeiro.repository.TituloFinanceiroRepository;
import com.vivo4redes.syscor.financeiro.repository.WebhookLogRepository;
import com.vivo4redes.syscor.financeiro.service.ConciliacaoProcessor;
import com.vivo4redes.syscor.financeiro.service.ConciliacaoStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** API consumida pelo React (exige a autenticação normal do sistema). */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ConciliacaoController {

    private final TituloFinanceiroRepository titulos;
    private final WebhookLogRepository logs;
    private final ConciliacaoProcessor processor;
    private final ConciliacaoStream stream;

    @GetMapping("/contas-pagar")
    public List<TituloPagarDTO> contasPagar() {
        return titulos.findByTipo(TipoTitulo.PAGAR).stream().map(TituloPagarDTO::de).toList();
    }

    @GetMapping("/conciliacao/logs")
    public List<WebhookLogDTO> logs(@RequestParam(defaultValue = "50") int tamanho) {
        return logs.findAllByOrderByRecebidoEmDesc(PageRequest.of(0, Math.min(tamanho, 200)))
                .stream().map(WebhookLogDTO::de).toList();
    }

    @PostMapping("/conciliacao/logs/{id}/reprocessar")
    public ResponseEntity<Void> reprocessar(@PathVariable Long id) {
        boolean emErro = logs.findById(id).filter(l -> l.getStatus() == StatusWebhook.ERRO).isPresent();
        if (!emErro) return ResponseEntity.status(409).build();
        processor.reprocessar(id);
        return ResponseEntity.accepted().build();
    }

    @GetMapping(value = "/conciliacao/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return stream.assinar();
    }
}