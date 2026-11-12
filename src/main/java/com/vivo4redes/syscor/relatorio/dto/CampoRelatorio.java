package com.vivo4redes.syscor.relatorio.dto;

import com.vivo4redes.syscor.relatorio.enums.TipoRelatorio;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Campo escolhível de um relatório. {@code coluna} é sempre uma coluna da view (fixa no código; a API só recebe a
 * {@code chave}, nunca SQL). Só entram campos com origem no banco (Fase A, docs/relatorios_produtos_servicos.md).
 *
 * @param somenteExcel sai só no arquivo, não na tabela da tela
 * @param travado      sempre presente, não dá para desmarcar
 */
public record CampoRelatorio(String chave, String rotulo, String coluna, Formato formato,
                             boolean somenteExcel, boolean padrao, boolean travado) {

    public enum Formato { TEXTO, INTEIRO, DECIMAL, DATA, DATA_HORA, HORA, BOOLEANO }

    private static CampoRelatorio campo(String chave, String rotulo, String coluna, Formato f) {
        return new CampoRelatorio(chave, rotulo, coluna, f, false, false, false);
    }

    private static CampoRelatorio excel(String chave, String rotulo, String coluna, Formato f) {
        return new CampoRelatorio(chave, rotulo, coluna, f, true, false, false);
    }

    private CampoRelatorio marcado() {
        return new CampoRelatorio(chave, rotulo, coluna, formato, somenteExcel, true, travado);
    }

    private CampoRelatorio fixo() {
        return new CampoRelatorio(chave, rotulo, coluna, formato, somenteExcel, true, true);
    }

    private static final CampoRelatorio FILIAL = campo("filial", "Filial", "filial", Formato.TEXTO).fixo();

    /** Campos de cada relatório, na ordem em que aparecem na tela e no arquivo. */
    private static final Map<TipoRelatorio, List<CampoRelatorio>> CATALOGO = Map.of(
            TipoRelatorio.PRODUTOS, List.of(
                    FILIAL,
                    campo("numeroVenda", "Nº da Venda", "numero_venda", Formato.INTEIRO).fixo(),
                    campo("marca", "Marca do produto", "marca", Formato.TEXTO).marcado(),
                    campo("modeloComercial", "Modelo Comercial", "modelo_comercial", Formato.TEXTO).marcado(),
                    campo("modeloDpgc", "Modelo DPGC", "modelo_dpgc", Formato.TEXTO),
                    campo("plano", "Plano", "plano", Formato.TEXTO),
                    campo("serial", "Serial", "serial_imei", Formato.TEXTO).marcado(),
                    campo("fornecedor", "Fornecedor", "fornecedor", Formato.TEXTO),
                    campo("valorCusto", "Valor de Custo", "valor_custo", Formato.DECIMAL).marcado(),
                    campo("descontoVivo", "Valor de desconto Vivo", "desconto_fabricante", Formato.DECIMAL),
                    campo("desconto", "Valor de desconto", "desconto", Formato.DECIMAL),
                    campo("valorAdicional", "Valor Adicional", "valor_adicional", Formato.DECIMAL),
                    campo("valorVenda", "Valor de venda", "valor_venda", Formato.DECIMAL).marcado(),
                    campo("valorLucro", "Valor Lucro", "valor_lucro", Formato.DECIMAL),
                    campo("cor", "Cor", "cor", Formato.TEXTO),
                    campo("quantidade", "Quantidade", "quantidade", Formato.INTEIRO),
                    campo("dataCompra", "Data da compra", "data_compra", Formato.DATA),
                    campo("dataVenda", "Data da venda", "data_venda::DATE", Formato.DATA).marcado(),
                    campo("vendedor", "Vendedor", "vendedor", Formato.TEXTO),
                    excel("horaVenda", "Hora da venda (excel)", "hora_venda", Formato.HORA).marcado(),
                    campo("filialCompra", "Filial de compra", "filial_compra", Formato.TEXTO),
                    campo("cliente", "Cliente", "cliente", Formato.TEXTO),
                    campo("ncm", "NCM / Nº Fiscal", "ncm", Formato.TEXTO),
                    campo("sku", "SKU", "sku", Formato.TEXTO),
                    campo("numeroComprovante", "Nº Comprovante Fiscal", "numero_nota", Formato.TEXTO),
                    campo("dataEmissaoNota", "Data de Emissão da Nota", "data_emissao_nota", Formato.DATA),
                    campo("custoLiquido", "Custo Líquido", "custo_liquido", Formato.DECIMAL),
                    campo("descontoFabricante", "Desconto Fabricante", "desconto_fabricante", Formato.DECIMAL),
                    campo("tabelaPreco", "Tabela de preço", "tabela_preco", Formato.TEXTO),
                    campo("numeroAcesso", "Nº de Acesso", "numero_acesso", Formato.TEXTO),
                    campo("uf", "UF", "uf", Formato.TEXTO),
                    campo("usuarioAutorizacao", "Usuário Autorização", "usuario_autorizacao", Formato.TEXTO),
                    excel("tipoProduto", "Tipo de produto (excel)", "tipo_produto", Formato.TEXTO).marcado(),
                    excel("categoriaTipo", "Categoria Tipo (excel)", "categoria_estoque", Formato.TEXTO),
                    excel("subcategoria", "Subcategoria (excel)", "subcategoria", Formato.TEXTO),
                    excel("condicaoPagamento", "Condição Pagamento (excel)", "condicao_pagamento", Formato.TEXTO),
                    excel("valorProduto", "Valor Produto (excel)", "valor_produto", Formato.DECIMAL),
                    excel("cupom", "Cupom de Desconto (excel)", "cupom", Formato.BOOLEANO),
                    campo("numeroVoucher", "Nº do Voucher Renova", "codigo_voucher", Formato.TEXTO)),
            TipoRelatorio.SERVICOS, List.of(
                    FILIAL,
                    campo("vendedor", "Vendedor", "vendedor", Formato.TEXTO).marcado(),
                    campo("cliente", "Cliente", "cliente", Formato.TEXTO).marcado(),
                    excel("dataInput", "Data de Input da Venda (somente excel)", "data_input", Formato.DATA_HORA),
                    campo("dataVenda", "Data da venda", "data_venda::DATE", Formato.DATA),
                    campo("dataAtivacao", "Data de Ativação", "data_ativacao", Formato.DATA),
                    excel("horaVenda", "Hora da venda (somente excel)", "hora_venda", Formato.HORA),
                    excel("numeroNota", "Nº Nota (somente excel)", "numero_nota", Formato.TEXTO),
                    excel("serieNota", "Série Nota (somente excel)", "serie_nota", Formato.TEXTO),
                    excel("idServico", "ID Serviço (somente excel)", "item_id", Formato.INTEIRO),
                    campo("numeroVenda", "Nº da Venda", "numero_venda", Formato.INTEIRO),
                    campo("tipoServico", "Serviço", "tipo_servico", Formato.TEXTO),
                    campo("tipoLinha", "Tipo Linha", "tipo_linha", Formato.TEXTO),
                    campo("trocaFidelizada", "Troca Fidelizada", "fidelizacao", Formato.BOOLEANO),
                    campo("marcaModelo", "Marca/Modelo", "descricao_produto", Formato.TEXTO),
                    campo("plano", "Plano", "plano", Formato.TEXTO),
                    campo("valorPlano", "Valor do Plano", "valor_plano", Formato.DECIMAL),
                    campo("portabilidade", "Portabilidade", "numero_portado", Formato.TEXTO),
                    campo("sistemaOrigem", "Sistema de Origem", "sistema_origem", Formato.TEXTO),
                    campo("imei", "IMEI", "serial_imei", Formato.TEXTO),
                    campo("iccid", "ICCID", "iccid", Formato.TEXTO),
                    campo("diaVencimento", "Dia de Vencimento", "vencimento_fatura", Formato.TEXTO),
                    campo("valorReceita", "Valor Receita", "valor_receita", Formato.DECIMAL).marcado(),
                    campo("numeroAcesso", "Nº de Acesso", "numero_acesso", Formato.TEXTO),
                    campo("dataInstalacao", "Data da Instalação", "data_instalacao", Formato.DATA),
                    campo("statusBko", "Status BKO", "status_bko", Formato.TEXTO),
                    campo("statusInstalacao", "Status da Instalação", "servico_instalado", Formato.BOOLEANO),
                    campo("numeroProtocoloGed", "Nº Protocolo GED", "numero_protocolo_ged", Formato.TEXTO),
                    campo("dataDigitalizacao", "Data de digitalização", "data_digitalizacao", Formato.DATA),
                    campo("uf", "UF", "uf", Formato.TEXTO),
                    excel("dataCancelamento", "Data de Cancelamento (somente excel)", "data_cancelamento", Formato.DATA),
                    excel("liderEquipe", "Líder de Equipe (somente excel)", "lider_equipe", Formato.TEXTO),
                    campo("grupamentoPlano", "Grupamento Plano", "grupo_plano", Formato.TEXTO)),
            TipoRelatorio.VENDAS, List.of(
                    FILIAL,
                    campo("numeroVenda", "Nº da Venda", "numero_venda", Formato.INTEIRO).fixo(),
                    campo("statusVenda", "Status", "CASE status_venda WHEN 'CANCELADA' THEN 'Cancelada' ELSE 'Concluída' END",
                            Formato.TEXTO).marcado(),
                    campo("vendedor", "Vendedor", "vendedor", Formato.TEXTO).marcado(),
                    campo("cliente", "Cliente", "cliente", Formato.TEXTO).marcado(),
                    campo("cpfCnpj", "CPF/CNPJ", "cpf_cnpj", Formato.TEXTO),
                    campo("uf", "UF", "uf", Formato.TEXTO),
                    campo("dataVenda", "Data da venda", "data_venda::DATE", Formato.DATA).marcado(),
                    excel("horaVenda", "Hora da venda (somente excel)", "hora_venda", Formato.HORA),
                    excel("dataInput", "Data de Input da Venda (somente excel)", "data_input", Formato.DATA_HORA),
                    campo("dataAtivacao", "Data de Ativação", "data_ativacao", Formato.DATA),
                    campo("qtdItens", "Itens", "qtd_itens", Formato.INTEIRO),
                    campo("qtdProdutos", "Produtos", "qtd_produtos", Formato.INTEIRO),
                    campo("qtdServicos", "Serviços", "qtd_servicos", Formato.INTEIRO),
                    campo("valorItens", "Valor dos itens", "valor_itens", Formato.DECIMAL),
                    campo("ajusteCondicao", "Ajuste da condição", "ajuste_condicao", Formato.DECIMAL),
                    campo("valorTotal", "Valor Total", "valor_total", Formato.DECIMAL).marcado(),
                    campo("condicaoPagamento", "Condição Pagamento", "condicao_pagamento", Formato.TEXTO),
                    campo("numeroVoucher", "Nº do Voucher Renova", "codigo_voucher", Formato.TEXTO),
                    excel("numeroNota", "Nº Nota (somente excel)", "numero_nota", Formato.TEXTO),
                    excel("serieNota", "Série Nota (somente excel)", "serie_nota", Formato.TEXTO),
                    campo("statusBko", "Status BKO", "status_bko", Formato.TEXTO),
                    campo("usuarioAutorizacao", "Usuário Autorização", "usuario_autorizacao", Formato.TEXTO),
                    excel("dataCancelamento", "Data de Cancelamento (somente excel)", "data_cancelamento", Formato.DATA),
                    excel("usuarioCancelamento", "Usuário Cancelamento (somente excel)", "usuario_cancelamento", Formato.TEXTO)));

    public static List<CampoRelatorio> doRelatorio(TipoRelatorio tipo) {
        return CATALOGO.get(tipo);
    }

    /**
     * Campos a mostrar, na ordem do catálogo: os escolhidos (por chave) mais os travados; sem escolha, os marcados por
     * padrão. {@code paraTela} tira os "somente excel". Chave desconhecida é ignorada.
     */
    public static List<CampoRelatorio> escolhidos(TipoRelatorio tipo, List<String> chaves, boolean paraTela) {
        boolean semEscolha = chaves == null || chaves.isEmpty();
        List<CampoRelatorio> saida = new ArrayList<>();
        for (CampoRelatorio c : CATALOGO.get(tipo)) {
            boolean incluir = c.travado() || (semEscolha ? c.padrao() : chaves.contains(c.chave()));
            if (incluir && !(paraTela && c.somenteExcel())) saida.add(c);
        }
        return saida;
    }

    public static Stream<String> chaves(TipoRelatorio tipo) {
        return CATALOGO.get(tipo).stream().map(CampoRelatorio::chave);
    }
}
