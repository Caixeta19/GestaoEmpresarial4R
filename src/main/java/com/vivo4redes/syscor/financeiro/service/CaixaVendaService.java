package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.exception.CaixaException;
import com.vivo4redes.syscor.exception.NegocioException;
import com.vivo4redes.syscor.financeiro.config.CaixaProperties;
import com.vivo4redes.syscor.financeiro.enums.StatusCaixaSessao;
import com.vivo4redes.syscor.financeiro.model.CaixaSessao;
import com.vivo4redes.syscor.financeiro.repository.CaixaSessaoRepository;
import com.vivo4redes.syscor.venda.model.Filial;
import com.vivo4redes.syscor.venda.model.Venda;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A regra central do caixa para a venda: sem caixa aberto hoje na filial, não se cria carrinho, não se adiciona item e
 * não se lança venda. Vale para todos, admin inclusive. O dia é o de America/Sao_Paulo.
 */
@Service
public class CaixaVendaService {

    private static final ZoneId ZONA = ZoneId.of("America/Sao_Paulo");
    private static final DateTimeFormatter DD_MM = DateTimeFormatter.ofPattern("dd/MM");

    private final CaixaSessaoRepository caixaRepository;
    private final CaixaProperties props;

    public CaixaVendaService(CaixaSessaoRepository caixaRepository, CaixaProperties props) {
        this.caixaRepository = caixaRepository;
        this.props = props;
    }

    /** Criar carrinho e adicionar item: basta haver um caixa aberto hoje na filial. */
    @Transactional(readOnly = true)
    public void exigirCaixaAbertoHoje(Filial filial) {
        abertosHoje(filial);
    }

    /**
     * Lançar a venda: devolve o caixa em que ela entra. Com um só caixa aberto é ele; com mais de um, a venda precisa
     * dizer qual ({@code caixaSessaoId}).
     */
    @Transactional(readOnly = true)
    public CaixaSessao caixaParaFinalizar(Filial filial, Long caixaSessaoId) {
        List<CaixaSessao> abertos = abertosHoje(filial);
        if (caixaSessaoId != null) {
            return abertos.stream().filter(c -> c.getId().equals(caixaSessaoId)).findFirst()
                    .orElseThrow(() -> new CaixaException("CAIXA_NAO_ABERTO", HttpStatus.CONFLICT,
                            "O caixa informado não está aberto hoje nessa filial."));
        }
        if (abertos.size() > 1) {
            throw new NegocioException("A filial tem mais de um caixa aberto hoje: informe o caixaSessaoId da venda.");
        }
        return abertos.get(0);
    }

    /**
     * Cancelar venda já lançada: o caixa dela precisa estar ABERTO; fechado, só depois de reaberto. Venda sem caixa
     * (anterior à V38) segue como antes.
     */
    @Transactional(readOnly = true)
    public void exigirCaixaAbertoParaCancelar(Venda venda) {
        CaixaSessao caixa = venda.getCaixaSessao();
        if (caixa == null || caixa.getStatus() == StatusCaixaSessao.ABERTO) return;
        throw CaixaException.fechado("O caixa dessa venda (" + caixa.getCaixaPdv() + ", de "
                + caixa.getDataAbertura().format(DD_MM) + ") já foi fechado. Peça à auditoria de caixa ou ao financeiro "
                + "para reabri-lo antes de cancelar a venda.");
    }

    private List<CaixaSessao> abertosHoje(Filial filial) {
        LocalDate hoje = LocalDate.now(ZONA);
        List<CaixaSessao> abertos = caixaRepository.findByFilialIdAndDataAberturaAndStatusOrderByIdAsc(
                filial.getId(), hoje, StatusCaixaSessao.ABERTO);
        if (abertos.isEmpty()) {
            throw CaixaException.naoAberto();
        }
        if (props.isBloquearComCaixaAnteriorAberto()) {
            List<CaixaSessao> anteriores = caixaRepository.findByFilialIdAndStatusAndDataAberturaBeforeOrderByDataAberturaAsc(
                    filial.getId(), StatusCaixaSessao.ABERTO, hoje);
            if (!anteriores.isEmpty()) {
                throw new CaixaException("CAIXA_ANTERIOR_ABERTO", HttpStatus.CONFLICT,
                        "Há caixa de dia anterior ainda aberto (" + anteriores.get(0).getCaixaPdv() + ", de "
                                + anteriores.get(0).getDataAbertura().format(DD_MM) + "). Feche-o antes de lançar vendas.");
            }
        }
        return abertos;
    }
}
