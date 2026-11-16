#!/usr/bin/env node
// Mascara uma resposta REAL da API Extratos v2 do BB para você poder colar no chat sem expor dados.
//
// Uso:   node scripts/mascarar-extrato-bb.mjs resposta.json
//        node scripts/mascarar-extrato-bb.mjs resposta.json > mascarado.json
//
// Segurança: lista de PERMISSÃO. Só passam, em claro, os campos que a calibração precisa e que não identificam ninguém
// (códigos de histórico, tipo, sinal, datas). Todo o resto é substituído por um formato (letras viram X, dígitos viram 9).
// Campo desconhecido é mascarado por padrão. O script só lê o arquivo: não faz rede e não grava nada.
// Mesmo assim, CONFIRA a saída antes de colar.

import { readFileSync } from 'node:fs';

const arquivo = process.argv[2];
if (!arquivo) {
  console.error('Uso: node scripts/mascarar-extrato-bb.mjs <resposta.json>');
  process.exit(2);
}

let corpo;
try {
  corpo = JSON.parse(readFileSync(arquivo, 'utf8'));
} catch (e) {
  console.error(`Não consegui ler o JSON: ${e.message}`);
  process.exit(2);
}

// Palavras de extrato que descrevem a operação e não identificam pessoa. Tudo fora desta lista vira X.
const PALAVRAS_SEGURAS = new Set([
  'PIX', 'TED', 'DOC', 'BOLETO', 'PAGAMENTO', 'PAGTO', 'PGTO', 'PAG', 'TARIFA', 'IOF', 'ENVIADO', 'ENVIADA', 'RECEBIDO',
  'RECEBIDA', 'TRANSF', 'TRANSFERENCIA', 'TRANSFERÊNCIA', 'DEPOSITO', 'DEPÓSITO', 'SAQUE', 'COMPRA', 'DEBITO', 'DÉBITO',
  'CREDITO', 'CRÉDITO', 'CARTAO', 'CARTÃO', 'AGENDADO', 'AGENDAMENTO', 'CONVENIO', 'CONVÊNIO', 'TRIBUTO', 'GUIA', 'DARF',
  'GPS', 'FGTS', 'DAS', 'IPTU', 'IPVA', 'TITULO', 'TÍTULO', 'COBRANCA', 'COBRANÇA', 'CHAVE', 'QR', 'CODE', 'DINAMICO',
  'ESTATICO', 'DE', 'DA', 'DO', 'EM', 'NO', 'NA', 'E', 'A', 'O', 'CPF', 'CNPJ', 'PJ', 'PF', 'CC', 'CP', 'SALDO', 'ANTERIOR',
  'DISPONIVEL', 'DISPONÍVEL', 'ONLINE', 'INTERNET', 'BANKING', 'APP', 'ENTRE', 'CONTAS', 'MESMA', 'TITULARIDADE',
]);

// Campos em claro (a calibração precisa deles e eles não identificam ninguém).
const EM_CLARO = new Set([
  'indicadorTipoLancamento', 'indicadorSinalLancamento', 'codigoHistorico', 'codigoSubHistorico', 'indicadorTipoPessoaContrapartida',
  'codigoBancoContrapartida', 'dataLancamento', 'dataMovimento',
  'numeroPaginaAtual', 'quantidadeRegistroPaginaAtual', 'numeroPaginaAnterior', 'numeroPaginaProximo',
  'quantidadeTotalPagina', 'quantidadeTotalRegistro',
]);

const formato = (texto) => String(texto).replace(/[0-9]/g, '9').replace(/[A-Za-zÀ-ÿ]/g, 'X');

function mascararTexto(texto) {
  return String(texto)
    .split(/(\s+)/)
    .map((p) => (PALAVRAS_SEGURAS.has(p.toUpperCase()) ? p : formato(p)))
    .join('');
}

// Valor: mantém só o jeito (quantas casas, se é número ou texto), nunca o montante.
const mascararValor = (v) => (typeof v === 'number'
  ? `<número com ${(String(v).split('.')[1] || '').length} casa(s) decimal(is)>`
  : `<${typeof v}>`);

function mascararCampo(chave, v) {
  if (v === null || v === undefined) return v;
  if (EM_CLARO.has(chave)) return v;
  if (chave === 'valorLancamento') return mascararValor(v);
  if (chave === 'textoDescricaoSubHistorico' || chave === 'textoInformacaoComplementar') return mascararTexto(v);
  if (chave === 'numeroCadastroPessoaFisicaCadastroNacPessoasJuridicasContrapartida') {
    const s = String(v);
    return s.trim() === '' || /^0+$/.test(s) ? `<vazio/zeros, ${s.length} posições>` : `<preenchido, ${s.length} posições, tipo ${typeof v}>`;
  }
  if (chave === 'textoIdentificadorUnicoTransacao') {
    const s = String(v).trim();
    return s === '' ? '<vazio>' : `<preenchido, ${s.length} caracteres, formato ${formato(s)}>`;
  }
  // Qualquer outro campo (documento, lote, agência, conta, nomes…): só o formato.
  return typeof v === 'object' ? '<objeto omitido>' : formato(v);
}

const lista = Array.isArray(corpo.listaLancamento) ? corpo.listaLancamento : [];
const saida = {};
for (const [k, v] of Object.entries(corpo)) {
  if (k !== 'listaLancamento') saida[k] = mascararCampo(k, v);
}
saida.listaLancamento = lista.map((item) => Object.fromEntries(Object.entries(item).map(([k, v]) => [k, mascararCampo(k, v)])));

// Resumo para a calibração: como cada tipo de operação aparece.
const resumo = {};
for (const item of lista) {
  if (String(item.indicadorTipoLancamento) !== '1' && String(item.indicadorTipoLancamento) !== '3') continue;
  const chave = `tipo=${item.indicadorTipoLancamento} sinal=${item.indicadorSinalLancamento} codigoHistorico=${item.codigoHistorico} sub="${mascararTexto(item.textoDescricaoSubHistorico ?? '')}"`;
  const r = (resumo[chave] ||= { quantidade: 0, comContrapartida: 0, comIdentificadorUnico: 0 });
  r.quantidade += 1;
  const doc = String(item.numeroCadastroPessoaFisicaCadastroNacPessoasJuridicasContrapartida ?? '');
  if (doc.trim() !== '' && !/^0+$/.test(doc)) r.comContrapartida += 1;
  if (String(item.textoIdentificadorUnicoTransacao ?? '').trim() !== '') r.comIdentificadorUnico += 1;
}
saida._resumoCalibracao = resumo;
saida._aviso = 'Mascarado por mascarar-extrato-bb.mjs. Confira antes de compartilhar.';

console.log(JSON.stringify(saida, null, 2));
