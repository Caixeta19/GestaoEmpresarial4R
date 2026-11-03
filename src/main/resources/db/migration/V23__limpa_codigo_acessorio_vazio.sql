-- codigo_acessorio gravado com marcadores de vazio vindos da planilha ('', "", ', ", -, null)
-- fazia aparelhos serem tratados como acessório. A importação agora grava NULL nesses casos
-- (ConversorValores.texto / EstoqueUtil.textoVazio); aqui limpamos o que já estava gravado.
-- Os literais abaixo são, nesta ordem: ''  ""  '  "  -  (vazio)

UPDATE tb_estoque_geral
SET codigo_acessorio = NULL
WHERE TRIM(codigo_acessorio) IN ('''''', '""', '''', '"', '-', '')
   OR LOWER(TRIM(codigo_acessorio)) = 'null';
