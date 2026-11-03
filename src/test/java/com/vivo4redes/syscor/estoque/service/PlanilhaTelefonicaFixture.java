package com.vivo4redes.syscor.estoque.service;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * Planilha da Telefônica FICTÍCIA, montada em memória com Apache POI (a real é confidencial e não entra no repositório).
 * <p>
 * {@code deslocamento} insere colunas vazias antes dos dados e entre os blocos, para provar que o parser localiza
 * tudo pelo cabeçalho e não por posição.
 *
 * <pre>
 * ÍNDICE      Vigência a partir de | 01/11/2026
 * PLANOS      3 planos: CONTROLE ALTO VALOR (alta), VIVO V (alta), PRÉ (legado)
 * PRODUTOS    SAP-IP17 iPhone 17 Pro Max 512GB · SAP-A15 Galaxy A15 128GB · ACC-FONE Fone Bluetooth XYZ
 * SMARTPHONES TABELA REGULAR [PRÉ | VIVO V] · TABELA RENOVA [DESCONTO | PRÉ | VIVO V] · TABELA ANTERIOR [PRÉ]
 * ELETRÔNICOS_LP_Não Conectados  Fone Bluetooth XYZ (sem plano)
 * </pre>
 */
final class PlanilhaTelefonicaFixture {

    static final String IPHONE = "iPhone 17 Pro Max 512GB";
    static final String GALAXY = "Galaxy A15 128GB";
    static final String FONE = "Fone Bluetooth XYZ";

    static final String PLANO_CONTROLE = "Vivo Controle 25GB";
    static final String PLANO_VIVO_V = "Vivo V Ilimitado";
    static final String PLANO_PRE = "Vivo Pré Turbo";

    private PlanilhaTelefonicaFixture() {
    }

    static MockMultipartFile arquivo(int deslocamento) {
        try (Workbook wb = workbook(deslocamento, true); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            wb.write(out);
            return new MockMultipartFile("arquivo", "tabela-precos.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", out.toByteArray());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static Workbook workbook(int deslocamento, boolean comPlanos) {
        Workbook wb = new XSSFWorkbook();
        indice(wb);
        if (comPlanos) planos(wb);
        produtos(wb);
        smartphones(wb, deslocamento);
        naoConectados(wb, deslocamento);
        wb.createSheet("Outlet"); // aba ignorada
        return wb;
    }

    private static void indice(Workbook wb) {
        Sheet s = wb.createSheet("ÍNDICE");
        linha(s, 0, 0, "TABELA DE PREÇOS");
        linha(s, 2, 1, "Vigência a partir de", "", "01/11/2026");
        linha(s, 3, 1, "PVP aparelho NÃO inclui Chip, cujo valor é R$13");
    }

    private static void planos(Workbook wb) {
        Sheet s = wb.createSheet("PLANOS");
        linha(s, 0, 0, "PRODUTO", "TIPO DE PLANO", "NOME DO PLANO CLIENTE SISTEMA", "SISTEMA", "GRUPAMENTO ATUAL",
                "COD SISTEMA", "COD SAP (USAR PARA VENDA MANUAL)");
        linha(s, 1, 0, "CONTROLE", "Alta / Migração", PLANO_CONTROLE, "NGIN", "CONTROLE ALTO VALOR", "CTL25", "SAP25");
        linha(s, 2, 0, "PÓS", "ALTA", PLANO_VIVO_V, "ATLYS", "VIVO V", "VV01", "SAPVV");
        linha(s, 3, 0, "PRÉ", "Legado", PLANO_PRE, "NGIN", "PRÉ", "PRE01", "");
        linha(s, 4, 0, "PÓS", "ALTA", "Plano Sem Grupo", "ATLYS", "GRUPO INEXISTENTE", "X1", "");
    }

    private static void produtos(Workbook wb) {
        Sheet s = wb.createSheet("PRODUTOS");
        linha(s, 0, 0, "Cod_Sap", "Descricao_Sap", "Modelo (Nome DPGC)", "Cor", "Nome Comercial", "Fabricante",
                "Categoria", "Subcategoria");
        linha(s, 1, 0, "SAP-IP17", "IPHONE 17 PM 512 PRETO", "iPhone 17 Pro Max", "Preto", IPHONE, "Apple", "Smartphone", "");
        linha(s, 2, 0, "SAP-A15", "GALAXY A15 128 AZUL", "Galaxy A15", "Azul", GALAXY, "Samsung", "Smartphone", "");
        linha(s, 3, 0, "ACC-FONE", "FONE BT XYZ", "Fone XYZ", "Branco", FONE, "XYZ", "Acessório", "Áudio");
    }

    private static void smartphones(Workbook wb, int d) {
        Sheet s = wb.createSheet("SMARTPHONES");
        // Colunas: 0..4 fixas (+d), REGULAR a partir de 5+d, RENOVA a partir de 16+2d, ANTERIOR a partir de 22+3d.
        int reg = 5 + d, ren = 16 + 2 * d, ant = 22 + 3 * d;
        linha(s, 0, 0, "SMARTPHONES — fictício");

        Row blocos = s.createRow(1);
        blocos.createCell(reg).setCellValue("TABELA REGULAR");
        blocos.createCell(ren).setCellValue("TABELA RENOVA");
        blocos.createCell(ant).setCellValue("TABELA ANTERIOR");

        Row cab = s.createRow(2);
        String[] fixas = {"PORTFÓLIO", "CATEGORIA", "MARCA", "OFERTA DE COMUNICAÇÃO", "Nome Comercial"};
        for (int i = 0; i < fixas.length; i++) cab.createCell(d + i).setCellValue(fixas[i]);
        // REGULAR: PRÉ | PIX | 2x | 12x | 13x | 21x || VIVO V | PIX | 2x | 13x | 21x
        String[] regular = {"PRÉ", "PIX e Vivo Pay", "2x", "12x", "13x", "21x", "VIVO\nV", "PIX e Vivo Pay", "2x", "13x", "21x"};
        for (int i = 0; i < regular.length; i++) cab.createCell(reg + i).setCellValue(regular[i]);
        // RENOVA: DESCONTO | PRÉ | PIX || VIVO V | PIX | 13x
        String[] renova = {"DESCONTO", "PRÉ", "PIX e Vivo Pay", "VIVO V", "PIX e Vivo Pay", "13x"};
        for (int i = 0; i < renova.length; i++) cab.createCell(ren + i).setCellValue(renova[i]);
        String[] anterior = {"PRÉ", "PIX e Vivo Pay"};
        for (int i = 0; i < anterior.length; i++) cab.createCell(ant + i).setCellValue(anterior[i]);

        // iPhone: números e texto misturados (o "10500.10" e o "13500.13" vêm como texto).
        Row ip = s.createRow(3);
        texto(ip, d, "Smartphones", "Premium", "Apple", "Vivo Pós com Netflix", IPHONE);
        valores(ip, reg, 12909.00, 11618.10, 12909.00, 12909.00, "13500.13", 14000.21,
                10149.00, 9134.10, 10149.00, "10500.10", 10900.99);
        valores(ip, ren, 909.00, 12000.00, 10800.00, 9500.00, 8550.00, 9800.00);
        valores(ip, ant, 99999.00, 99999.00);

        // Galaxy: preço em texto com formato brasileiro; sem preço no Renova (linha não gera RENOVA).
        Row ga = s.createRow(4);
        texto(ga, d, "Smartphones", "Entrada", "Samsung", "Oferta X", GALAXY);
        valores(ga, reg, "1.299,00", "1.169,10", null, null, null, null, 999.00, 899.10, null, null, null);
        valores(ga, ren, null, "-", null, null, null, null);

        // Linha sem Nome Comercial e linha sem preço numérico: puladas.
        Row semNome = s.createRow(5);
        valores(semNome, reg, 1.00);
        Row semPreco = s.createRow(6);
        texto(semPreco, d, "", "", "", "", "Aparelho Sem Preço");
        valores(semPreco, reg, "consulte", null, null, null, null, null, "-");
    }

    private static void naoConectados(Workbook wb, int d) {
        Sheet s = wb.createSheet("ELETRÔNICOS_LP_Não Conectados");
        linha(s, 0, 0, "ELETRÔNICOS NÃO CONECTADOS — fictício");
        Row cab = s.createRow(2);
        String[] titulos = {"Nome Comercial", "Fabricante", "PVP Base", "PIX e Vivo Pay", "2x", "13x"};
        for (int i = 0; i < titulos.length; i++) cab.createCell(d + i).setCellValue(titulos[i]);
        Row fone = s.createRow(3);
        texto(fone, d, FONE, "XYZ");
        valores(fone, d + 2, 199.90, 179.91, 199.90, 210.00);
    }

    // ------------------------------------------------------------------ helpers

    private static void linha(Sheet s, int r, int c0, String... valores) {
        Row row = s.getRow(r) == null ? s.createRow(r) : s.getRow(r);
        texto(row, c0, valores);
    }

    private static void texto(Row row, int c0, String... valores) {
        for (int i = 0; i < valores.length; i++) row.createCell(c0 + i).setCellValue(valores[i]);
    }

    /** Double vira célula numérica; String vira célula de texto; null deixa a célula vazia. */
    private static void valores(Row row, int c0, Object... valores) {
        for (int i = 0; i < valores.length; i++) {
            Object v = valores[i];
            if (v == null) continue;
            if (v instanceof Double n) row.createCell(c0 + i).setCellValue(n);
            else row.createCell(c0 + i).setCellValue(v.toString());
        }
    }
}
