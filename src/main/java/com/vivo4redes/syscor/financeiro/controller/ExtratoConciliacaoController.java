package com.vivo4redes.syscor.financeiro.controller;

import com.vivo4redes.syscor.financeiro.dto.request.IgnorarLancamentoRequestDTO;
import com.vivo4redes.syscor.financeiro.dto.request.VincularLancamentoRequestDTO;
import com.vivo4redes.syscor.financeiro.dto.response.ExtratoLancamentoDTO;
import com.vivo4redes.syscor.financeiro.dto.response.SincronizacaoExtratoDTO;
import com.vivo4redes.syscor.financeiro.enums.SituacaoExtrato;
import com.vivo4redes.syscor.financeiro.service.ConciliacaoExtratoService;
import com.vivo4redes.syscor.financeiro.service.ExtratoSincronizacaoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/** Conciliação das contas a pagar pelo extrato do banco. Quem pode o quê: {@code FinanceiroPerfilService}. */
@RestController
@RequestMapping("/financeiro/conciliacao/extrato")
@RequiredArgsConstructor
public class ExtratoConciliacaoController {

    private final ExtratoSincronizacaoService sincronizacao;
    private final ConciliacaoExtratoService conciliacao;

    /** "Sincronizar agora" (financeiro e admin). 409 com o canal desligado ou já sincronizando. */
    @PostMapping("/sincronizar")
    public SincronizacaoExtratoDTO sincronizar() {
        return sincronizacao.sincronizarAgora();
    }

    /** Fila de lançamentos; os AMBIGUO trazem as {@code sugestoes} (contas candidatas). */
    @GetMapping("/lancamentos")
    public List<ExtratoLancamentoDTO> lancamentos(
            @RequestParam(required = false) SituacaoExtrato situacao,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate de,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate ate) {
        return conciliacao.listar(situacao, de, ate);
    }

    @PostMapping("/lancamentos/{id}/vincular")
    public ExtratoLancamentoDTO vincular(@PathVariable Long id, @Valid @RequestBody VincularLancamentoRequestDTO dto) {
        return conciliacao.vincular(id, dto.tituloId(), dto.justificativa());
    }

    @PostMapping("/lancamentos/{id}/ignorar")
    public ExtratoLancamentoDTO ignorar(@PathVariable Long id, @Valid @RequestBody IgnorarLancamentoRequestDTO dto) {
        return conciliacao.ignorar(id, dto.motivo());
    }
}
