package com.vivo4redes.syscor.financeiro.dev;

import com.vivo4redes.syscor.financeiro.enums.FormaPagamentoTitulo;
import com.vivo4redes.syscor.financeiro.enums.TipoTitulo;
import com.vivo4redes.syscor.financeiro.model.TituloFinanceiro;
import com.vivo4redes.syscor.financeiro.repository.TituloFinanceiroRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Contas a pagar fictícias de demonstração que casam com {@code dev/extrato-fake-dev.json}. Só é chamado pelo
 * {@link DevExtratoSeedRunner} (profile dev); não é migração para nunca ir a produção. Idempotente: cada conta é
 * identificada pela descrição com o prefixo {@link #PREFIXO}.
 */
@Service
public class DevExtratoSeedService {

    public static final String PREFIXO = "[DEMO-EXTRATO] ";

    private final TituloFinanceiroRepository titulos;

    public DevExtratoSeedService(TituloFinanceiroRepository titulos) {
        this.titulos = titulos;
    }

    /** @return quantas contas foram criadas nesta chamada (0 se já existiam) */
    @Transactional
    public int semear() {
        LocalDate vencimento = LocalDate.now(ZoneId.of("America/Sao_Paulo")).plusDays(5);
        int criadas = 0;
        // conciliada sozinha: valor e CNPJ iguais ao lançamento
        criadas += criar("Fornecedor conciliado", "Demo Conciliado Ltda", "11111111000111", "1234.50", vencimento);
        // ambígua: duas contas com o mesmo valor, sem documento
        criadas += criar("Ambigua A", "Demo Ambigua A Ltda", null, "777.77", vencimento);
        criadas += criar("Ambigua B", "Demo Ambigua B Ltda", null, "777.77", vencimento);
        // divergente: mesmo CNPJ do lançamento, valor diferente (400,00 x 380,00)
        criadas += criar("Fornecedor divergente", "Demo Divergente Ltda", "22222222000122", "400.00", vencimento);
        return criadas;
    }

    private int criar(String descricao, String quem, String documento, String valor, LocalDate vencimento) {
        String chave = PREFIXO + descricao;
        if (titulos.existsByTipoAndDescricao(TipoTitulo.PAGAR, chave)) return 0;
        titulos.save(TituloFinanceiro.builder()
                .tipo(TipoTitulo.PAGAR)
                .descricao(chave)
                .quem(quem)
                .documentoFornecedor(documento)
                .valor(new BigDecimal(valor))
                .vencimento(vencimento)
                .formaPagamento(FormaPagamentoTitulo.BOLETO)
                .build());
        return 1;
    }
}
