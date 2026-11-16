package com.vivo4redes.syscor.financeiro.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.vivo4redes.syscor.exception.FinanceiroException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.financeiro.config.BbExtratoProperties;
import com.vivo4redes.syscor.financeiro.enums.AcaoTitulo;
import com.vivo4redes.syscor.financeiro.enums.OrigemBaixa;
import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import com.vivo4redes.syscor.financeiro.model.ExtratoLancamento;
import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;
import com.vivo4redes.syscor.financeiro.model.TituloHistorico;
import com.vivo4redes.syscor.financeiro.repository.ExtratoLancamentoRepository;
import com.vivo4redes.syscor.financeiro.repository.TituloFinanceiroRepository;
import com.vivo4redes.syscor.financeiro.repository.TituloHistoricoRepository;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Comprovante de pagamento em PDF da conta baixada, gerado sob demanda (o PDF não é gravado). O extrato do banco não
 * é comprovante: o documento diz isso no rodapé. Só conta {@code BAIXADO} tem comprovante (409 {@code CONTA_NAO_BAIXADA}).
 * A primeira geração grava {@code COMPROVANTE} no histórico da conta.
 */
@Service
@RequiredArgsConstructor
public class ComprovanteContaPagarService {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final TituloFinanceiroRepository titulos;
    private final TituloHistoricoRepository historicos;
    private final ExtratoLancamentoRepository lancamentos;
    private final BbExtratoProperties bbProps;
    private final FinanceiroPerfilService perfil;
    private final UsuarioService usuarioService;

    /** Trava a conta para a primeira geração gravar um único {@code COMPROVANTE} mesmo com duas telas abertas. */
    @Transactional
    public byte[] gerar(Long id) {
        Usuario usuario = usuarioService.usuarioLogado();
        perfil.exigirConsulta(usuario);

        TituloFinanceiro conta = titulos.buscarComTrava(id, TipoTitulo.PAGAR)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta a pagar"));
        if (conta.getStatus() != StatusTitulo.BAIXADO) {
            throw FinanceiroException.naoBaixada();
        }

        List<TituloHistorico> historico = historicos.findByTituloIdOrderByEmAscIdAsc(id);
        byte[] pdf = renderizar(ComprovanteContaPagarHtml.montar(dadosDe(conta, historico)));

        if (historico.stream().noneMatch(h -> h.getAcao() == AcaoTitulo.COMPROVANTE)) {
            historicos.save(TituloHistorico.builder().titulo(conta).acao(AcaoTitulo.COMPROVANTE).usuario(usuario)
                    .texto("Comprovante gerado.").build());
        }
        return pdf;
    }

    ComprovanteContaPagarHtml.Dados dadosDe(TituloFinanceiro conta, List<TituloHistorico> historico) {
        boolean manual = conta.getOrigemBaixa() == OrigemBaixa.MANUAL;
        TituloHistorico baixa = historico.stream()
                .filter(h -> h.getAcao() == AcaoTitulo.BAIXA_MANUAL || h.getAcao() == AcaoTitulo.BAIXA_AUTOMATICA
                        || h.getAcao() == AcaoTitulo.VINCULOU)
                .reduce((primeira, ultima) -> ultima).orElse(null);

        String baixadoPor = conta.getBaixadoPor() != null ? conta.getBaixadoPor().getNome()
                : (baixa != null && baixa.getUsuario() != null ? baixa.getUsuario().getNome() : null);
        String baixadoEm = baixa == null ? null
                : DATA_HORA.format(baixa.getEm().atZone(ContaPagarService.FUSO));

        LocalDate dataPagamento = conta.getPagoEm() != null
                ? conta.getPagoEm().atZoneSameInstant(ContaPagarService.FUSO).toLocalDate()
                : conta.getDataBaixa();

        ExtratoLancamento lancamento = conta.getExtratoLancamentoId() == null ? null
                : lancamentos.findById(conta.getExtratoLancamentoId()).orElse(null);
        if (lancamento != null) dataPagamento = lancamento.getDataLancamento();

        String justificativa = conta.getJustificativaBaixa() == null ? "" : conta.getJustificativaBaixa();
        String origem;
        if (manual) {
            String quem = baixadoPor == null ? "usuário não identificado" : baixadoPor;
            String quando = dataPagamento == null ? "" : " em " + dataPagamento.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
            origem = "Baixa manual por " + quem + quando + ": " + justificativa;
        } else if (conta.getBaixadoPor() != null) {   // vínculo confirmado por uma pessoa
            origem = "Conciliação com extrato do Banco do Brasil confirmada por " + conta.getBaixadoPor().getNome()
                    + (justificativa.isEmpty() ? "" : ": " + justificativa);
        } else {
            origem = "Conciliação automática com extrato do Banco do Brasil";
        }
        String historicoLancamento = lancamento == null || lancamento.historicoTexto().isEmpty() ? null
                : lancamento.historicoTexto();

        return new ComprovanteContaPagarHtml.Dados(
                conta.getId(),
                conta.getDescricao(),
                conta.getQuem(),
                conta.getDocumentoFornecedor(),
                conta.getFormaPagamento() == null ? null : conta.getFormaPagamento().name(),
                conta.getVencimento(),
                conta.getValor(),
                conta.getValorPago() != null ? conta.getValorPago() : conta.getValor(),
                dataPagamento != null ? dataPagamento : LocalDate.now(ContaPagarService.FUSO),
                conta.getBancoPagador(),
                lancamento == null ? null : ComprovanteContaPagarHtml.mascarar(bbProps.getConta()),
                historicoLancamento,
                lancamento != null ? lancamento.getIdExterno() : conta.getIdentificadorPagamento(),
                origem,
                baixadoPor,
                baixadoEm);
    }

    static byte[] renderizar(String html) {
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(html, null);   // sem base URI: nada de recurso externo
            builder.toStream(saida);
            builder.run();
            return saida.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao gerar o PDF do comprovante", e);
        }
    }
}
