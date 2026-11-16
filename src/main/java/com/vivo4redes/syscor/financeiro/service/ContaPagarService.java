package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.exception.FinanceiroException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.exception.RecursoNaoEncontradoException;
import com.vivo4redes.syscor.financeiro.dto.request.BaixaManualRequestDTO;
import com.vivo4redes.syscor.financeiro.dto.request.CancelarContaRequestDTO;
import com.vivo4redes.syscor.financeiro.dto.request.ContaPagarRequestDTO;
import com.vivo4redes.syscor.financeiro.dto.request.ReabrirContaRequestDTO;
import com.vivo4redes.syscor.financeiro.dto.response.ContaPagarDetalheDTO;
import com.vivo4redes.syscor.financeiro.dto.response.HistoricoTituloDTO;
import com.vivo4redes.syscor.financeiro.dto.response.PermissoesContasPagarDTO;
import com.vivo4redes.syscor.financeiro.dto.response.ResumoContasPagarDTO;
import com.vivo4redes.syscor.financeiro.dto.response.TituloPagarDTO;
import com.vivo4redes.syscor.financeiro.enums.AcaoTitulo;
import com.vivo4redes.syscor.financeiro.enums.OrigemBaixa;
import com.vivo4redes.syscor.financeiro.enums.StatusTitulo;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;
import com.vivo4redes.syscor.financeiro.model.TituloHistorico;
import com.vivo4redes.syscor.financeiro.repository.TituloFinanceiroRepository;
import com.vivo4redes.syscor.financeiro.repository.TituloHistoricoRepository;
import com.vivo4redes.syscor.util.TextoUtil;
import com.vivo4redes.syscor.venda.model.Usuario;
import com.vivo4redes.syscor.venda.repository.FilialRepository;
import com.vivo4redes.syscor.venda.service.UsuarioService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

/**
 * Contas a pagar: cadastro, edição, cancelamento, baixa manual e reabertura, sempre com auditoria em
 * {@code tb_titulo_historico}. Quem pode o quê: {@link FinanceiroPerfilService}. A baixa automática pelo extrato do
 * banco fica no serviço de conciliação do extrato; este serviço cuida do que a pessoa faz na tela.
 */
@Service
@RequiredArgsConstructor
public class ContaPagarService {

    static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    private final TituloFinanceiroRepository titulos;
    private final TituloHistoricoRepository historicos;
    private final FilialRepository filialRepository;
    private final FinanceiroPerfilService perfil;
    private final UsuarioService usuarioService;

    // ------------------------------------------------------------------ cadastro e edição
    @Transactional
    public TituloPagarDTO cadastrar(ContaPagarRequestDTO dto) {
        Usuario usuario = usuarioService.usuarioLogado();
        perfil.exigirGerenciar(usuario);

        TituloFinanceiro conta = TituloFinanceiro.builder()
                .tipo(TipoTitulo.PAGAR)
                .status(StatusTitulo.ABERTO)
                .criadoPor(usuario)
                .build();
        aplicar(conta, dto, null);
        conta = titulos.save(conta);
        registrar(conta, AcaoTitulo.CRIOU, usuario, "Conta cadastrada: " + conta.getQuem() + ", " + conta.getValor()
                + " com vencimento em " + conta.getVencimento() + ".");
        return TituloPagarDTO.de(conta);
    }

    @Transactional
    public TituloPagarDTO editar(Long id, ContaPagarRequestDTO dto) {
        Usuario usuario = usuarioService.usuarioLogado();
        perfil.exigirGerenciar(usuario);

        TituloFinanceiro conta = buscarComTrava(id);
        if (!conta.editavel()) {
            throw FinanceiroException.statusInvalido(conta.getStatus() == StatusTitulo.BAIXADO
                    ? "Conta baixada não pode ser editada. Reabra a baixa antes de corrigir."
                    : "Conta cancelada não pode ser editada.");
        }
        String antes = resumoMudanca(conta);
        aplicar(conta, dto, id);
        titulos.save(conta);
        registrar(conta, AcaoTitulo.EDITOU, usuario, "Antes: " + antes + " | Depois: " + resumoMudanca(conta));
        return TituloPagarDTO.de(conta);
    }

    // ------------------------------------------------------------------ cancelar, baixa manual, reabrir
    @Transactional
    public TituloPagarDTO cancelar(Long id, CancelarContaRequestDTO dto) {
        Usuario usuario = usuarioService.usuarioLogado();
        perfil.exigirGerenciar(usuario);

        TituloFinanceiro conta = buscarComTrava(id);
        exigirTransicao(conta, StatusTitulo.CANCELADO, "Só conta pendente ou divergente pode ser cancelada.");
        String motivo = dto.motivo().trim();
        conta.cancelar(usuario, motivo);
        titulos.save(conta);
        registrar(conta, AcaoTitulo.CANCELOU, usuario, motivo);
        return TituloPagarDTO.de(conta);
    }

    @Transactional
    public TituloPagarDTO baixaManual(Long id, BaixaManualRequestDTO dto) {
        Usuario usuario = usuarioService.usuarioLogado();
        perfil.exigirGerenciar(usuario);

        TituloFinanceiro conta = buscarComTrava(id);
        exigirTransicao(conta, StatusTitulo.BAIXADO, "Só conta pendente ou divergente pode ser baixada.");
        if (dto.dataPagamento().isAfter(LocalDate.now(FUSO))) {
            throw new NegocioException("A data do pagamento não pode ser futura.");
        }
        String justificativa = dto.justificativa().trim();
        OffsetDateTime quando = dto.dataPagamento().atStartOfDay(FUSO).toOffsetDateTime();
        conta.baixarManual(dto.valorPago(), quando, dto.banco().trim(), usuario, justificativa);
        titulos.save(conta);
        registrar(conta, AcaoTitulo.BAIXA_MANUAL, usuario, "Pago em " + dto.dataPagamento() + ", " + dto.valorPago()
                + " (" + dto.banco().trim() + "). " + justificativa);
        return TituloPagarDTO.de(conta);
    }

    /**
     * Desfaz uma baixa errada. Limpa valor/data/banco pagos e o vínculo com o lançamento do extrato (o serviço de
     * conciliação do extrato devolve o lançamento a "sem correspondência" a partir de {@code extratoLancamentoId}).
     */
    @Transactional
    public TituloPagarDTO reabrir(Long id, ReabrirContaRequestDTO dto) {
        Usuario usuario = usuarioService.usuarioLogado();
        perfil.exigirGerenciar(usuario);

        TituloFinanceiro conta = buscarComTrava(id);
        if (conta.getStatus() != StatusTitulo.BAIXADO) {
            throw FinanceiroException.statusInvalido("Só conta baixada pode ser reaberta.");
        }
        conta.reabrir();
        titulos.save(conta);
        registrar(conta, AcaoTitulo.REABRIU, usuario, dto.justificativa().trim());
        return TituloPagarDTO.de(conta);
    }

    // ------------------------------------------------------------------ consultas
    @Transactional(readOnly = true)
    public List<TituloPagarDTO> listar(StatusTitulo status, LocalDate vencimentoDe, LocalDate vencimentoAte,
                                       String busca, Long filialId) {
        perfil.exigirConsulta(usuarioService.usuarioLogado());
        String termo = busca == null || busca.isBlank() ? null : TextoUtil.chave(busca);
        String termoNumerico = termo == null ? null : termo.replaceAll("\\D", "");
        return titulos.findByTipo(TipoTitulo.PAGAR).stream()
                .filter(t -> status == null || t.getStatus() == status)
                .filter(t -> vencimentoDe == null || !t.getVencimento().isBefore(vencimentoDe))
                .filter(t -> vencimentoAte == null || !t.getVencimento().isAfter(vencimentoAte))
                .filter(t -> filialId == null || filialId.equals(t.getFilialId()))
                .filter(t -> termo == null || casaBusca(t, termo, termoNumerico))
                .sorted(Comparator.comparing(TituloFinanceiro::getVencimento).thenComparing(TituloFinanceiro::getId))
                .map(TituloPagarDTO::de)
                .toList();
    }

    @Transactional(readOnly = true)
    public ContaPagarDetalheDTO detalhe(Long id) {
        perfil.exigirConsulta(usuarioService.usuarioLogado());
        TituloFinanceiro conta = titulos.buscarPorIdETipo(id, TipoTitulo.PAGAR)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta a pagar"));
        List<HistoricoTituloDTO> historico = historicos.findByTituloIdOrderByEmAscIdAsc(id).stream()
                .map(h -> new HistoricoTituloDTO(h.getAcao(),
                        h.getUsuario() == null ? null : h.getUsuario().getNome(), h.getEm(), h.getTexto()))
                .toList();
        return new ContaPagarDetalheDTO(TituloPagarDTO.de(conta), conta.getJustificativaBaixa(),
                conta.getMotivoCancelamento(), historico);
    }

    @Transactional(readOnly = true)
    public ResumoContasPagarDTO resumo() {
        perfil.exigirConsulta(usuarioService.usuarioLogado());
        List<TituloFinanceiro> contas = titulos.findByTipo(TipoTitulo.PAGAR);
        LocalDate hoje = LocalDate.now(FUSO);

        BigDecimal totalEmAberto = BigDecimal.ZERO;
        long quantidadeEmAberto = 0, conciliadas = 0, divergencias = 0, vencidas = 0;
        for (TituloFinanceiro t : contas) {
            switch (t.getStatus()) {
                case ABERTO -> {
                    totalEmAberto = totalEmAberto.add(t.getValor());
                    quantidadeEmAberto++;
                    if (t.getVencimento().isBefore(hoje)) vencidas++;
                }
                case DIVERGENTE -> {
                    totalEmAberto = totalEmAberto.add(t.getValor());
                    quantidadeEmAberto++;
                    divergencias++;
                }
                case BAIXADO -> {
                    if (t.getOrigemBaixa() != OrigemBaixa.MANUAL) conciliadas++;
                }
                case CANCELADO -> { /* sai de todos os totais */ }
            }
        }
        // Canal bancário: a leitura do extrato chega na Fase D; até lá o canal está desligado.
        return new ResumoContasPagarDTO(totalEmAberto, quantidadeEmAberto, conciliadas, divergencias, vencidas,
                "DESLIGADO", null);
    }

    @Transactional(readOnly = true)
    public PermissoesContasPagarDTO permissoes() {
        Usuario usuario = usuarioService.usuarioLogado();
        boolean gerenciar = perfil.podeGerenciar(usuario);
        return new PermissoesContasPagarDTO(perfil.podeConsultar(usuario), gerenciar, gerenciar, gerenciar);
    }

    // ------------------------------------------------------------------ apoio
    /** Valida e copia o cadastro para a conta. {@code ignorarId} exclui a própria conta da checagem de duplicidade. */
    private void aplicar(TituloFinanceiro conta, ContaPagarRequestDTO dto, Long ignorarId) {
        String fornecedor = dto.fornecedor().trim();
        String codigoBarras = normalizarCodigoBarras(dto.codigoBarras());
        String documento = normalizarDocumento(dto.documentoFornecedor());
        BigDecimal valor = dto.valor().setScale(2, RoundingMode.UNNECESSARY);

        if (dto.filialId() != null && !filialRepository.existsById(dto.filialId())) {
            throw new RecursoNaoEncontradoException("Filial");
        }
        if (codigoBarras != null
                && titulos.existeCodigoBarras(TipoTitulo.PAGAR, codigoBarras, StatusTitulo.CANCELADO, ignorarId)) {
            throw FinanceiroException.contaDuplicada();
        }
        boolean confirmou = Boolean.TRUE.equals(dto.confirmarDuplicada());
        if (!confirmou && titulos.existeMesmaCombinacao(TipoTitulo.PAGAR, fornecedor, valor, dto.vencimento(),
                StatusTitulo.CANCELADO, ignorarId)) {
            throw FinanceiroException.contaProvavelDuplicada();
        }

        conta.setDescricao(dto.descricao().trim());
        conta.setQuem(fornecedor);
        conta.setVencimento(dto.vencimento());
        conta.setValor(valor);
        conta.setFormaPagamento(dto.formaPagamento());
        conta.setCodigoBarras(codigoBarras);
        conta.setDocumentoFornecedor(documento);
        conta.setFilialId(dto.filialId());
        conta.setCategoria(vazioParaNulo(dto.categoria()));
        conta.setObservacao(vazioParaNulo(dto.observacao()));
    }

    /** Linha digitável (47/48) ou código de barras (44): grava só os dígitos. */
    static String normalizarCodigoBarras(String bruto) {
        if (bruto == null || bruto.isBlank()) return null;
        String digitos = bruto.replaceAll("\\D", "");
        if (digitos.length() != 44 && digitos.length() != 47 && digitos.length() != 48) {
            throw new NegocioException("O código de barras deve ter 44 dígitos (código) ou 47/48 (linha digitável).");
        }
        return digitos;
    }

    static String normalizarDocumento(String bruto) {
        if (bruto == null || bruto.isBlank()) return null;
        String digitos = bruto.replaceAll("\\D", "");
        if (digitos.length() != 11 && digitos.length() != 14) {
            throw new NegocioException("O documento do fornecedor deve ter 11 dígitos (CPF) ou 14 (CNPJ).");
        }
        return digitos;
    }

    private static String vazioParaNulo(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }

    private static boolean casaBusca(TituloFinanceiro t, String termo, String termoNumerico) {
        if (TextoUtil.chave(t.getQuem()).contains(termo) || TextoUtil.chave(t.getDescricao()).contains(termo)) {
            return true;
        }
        if (termoNumerico == null || termoNumerico.isEmpty()) return false;
        return (t.getCodigoBarras() != null && t.getCodigoBarras().contains(termoNumerico))
                || (t.getDocumentoFornecedor() != null && t.getDocumentoFornecedor().contains(termoNumerico));
    }

    private static String resumoMudanca(TituloFinanceiro t) {
        return t.getQuem() + ", " + t.getValor() + ", venc. " + t.getVencimento();
    }

    private void exigirTransicao(TituloFinanceiro conta, StatusTitulo destino, String mensagem) {
        if (!conta.getStatus().podeTransicionarPara(destino)) {
            throw FinanceiroException.statusInvalido(mensagem);
        }
    }

    private TituloFinanceiro buscarComTrava(Long id) {
        return titulos.buscarComTrava(id, TipoTitulo.PAGAR)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conta a pagar"));
    }

    private void registrar(TituloFinanceiro conta, AcaoTitulo acao, Usuario usuario, String texto) {
        String limitado = texto == null ? null : (texto.length() > 500 ? texto.substring(0, 500) : texto);
        historicos.save(TituloHistorico.builder().titulo(conta).acao(acao).usuario(usuario).texto(limitado).build());
    }
}
