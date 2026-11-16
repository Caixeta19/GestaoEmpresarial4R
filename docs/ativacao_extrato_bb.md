# Ativação do extrato do Banco do Brasil (checklist, fora do código)

O SYSCOR já tem tudo pronto no código, **desligado** (`BB_EXTRATO_ENABLED=false`). Este roteiro é para você seguir com o gerente
de conta do BB e depois ligar com segurança. Nada aqui vai para o repositório: **o repositório é público**, então certificado,
senha, client id/secret e chave de aplicação ficam só em variável de ambiente ou cofre.

A API usada é a **Extratos v2** (a v1 sai do ar em 20/11/2026; o SYSCOR não tem nada da v1). A especificação oficial está em
`docs/bb/OpenAPI_BB_Extratos_v2.json`.

## 1. Com o BB e o gerente de conta

- [ ] **Conta corrente PJ** da empresa no BB (a conta cujo extrato será lido). Anote **agência e conta sem o dígito verificador e
      sem zeros à esquerda** (agência 0297 vira `297`; conta 001213 vira `1213`).
- [ ] **Cadastro no Portal Developers BB** (developers.bb.com.br), com o CNPJ da empresa. O gerente de conta ajuda a liberar.
- [ ] **Aplicação** no portal com a **API Extratos v2 habilitada** (escopo `extrato-info`). Peça a aplicação de **homologação**
      primeiro e, depois, a de **produção** (as credenciais de produção são geradas só em Produção).
- [ ] Da aplicação, copie em *Credenciais*: **client id**, **client secret** e a **developer application key**.
- [ ] **Certificado digital A1** (ICP-Brasil, **não** autoassinado) da empresa, para o **mTLS**. Exporte em **PKCS12** (`.p12`/`.pfx`)
      com senha. O BB precisa ter o certificado cadastrado na aplicação.
- [ ] Confirmar com o BB os **hosts** (estão na spec, mas confirme no portal):
      homologação `https://extratos.mtls.api.hm.bb.com.br/v2` e `https://oauth.hm.bb.com.br/oauth/token`;
      produção `https://extratos.mtls.api.bb.com.br/v2` e `https://oauth.bb.com.br/oauth/token`.

## 2. No servidor (variáveis de ambiente, nunca no git)

| Variável | Valor |
|---|---|
| `BB_EXTRATO_AGENCIA` / `BB_EXTRATO_CONTA` | só dígitos, sem verificador e sem zeros à esquerda |
| `BB_EXTRATO_CLIENT_ID` / `BB_EXTRATO_CLIENT_SECRET` | credenciais OAuth2 da aplicação |
| `BB_EXTRATO_APP_KEY` | developer application key (vai em `gw-dev-app-key`) |
| `BB_EXTRATO_KEYSTORE_PATH` / `BB_EXTRATO_KEYSTORE_PASSWORD` | caminho do `.p12` no servidor e a senha |
| `BB_EXTRATO_BASE_URL` / `BB_EXTRATO_TOKEN_URL` | homologação (padrão) ou produção |
| `BB_EXTRATO_ENABLED` | `false` até o teste de homologação passar |

O `.env` está no `.gitignore`. Guarde o `.p12` fora da pasta do projeto, com permissão só para o usuário do serviço.

## 3. Testar em homologação antes de ligar (Fase A)

1. Suba o SYSCOR com as variáveis de **homologação** e `BB_EXTRATO_ENABLED=true`.
2. Entre como financeiro/admin e use **Sincronizar agora** (`POST /api/financeiro/conciliacao/extrato/sincronizar`).
   - `status: OK` = o mTLS, o OAuth e a consulta funcionaram. `status: ERRO` traz a causa em `erro` (sem segredos).
3. **Pendências que só a homologação responde** (por isso a Fase A ficou aberta) e que você deve anotar e me passar, **com dados
   mascarados** (sem CPF/CNPJ, nome, agência ou conta reais):
   - [ ] o **formato do pedido de token que funcionou** (o SYSCOR usa `Basic` com id:secret e `grant_type=client_credentials&scope=extrato-info`);
   - [ ] se a data na query aceita o **zero à esquerda** (o SYSCOR envia o inteiro sem zero, como a spec declara, ex.: `5112026`);
   - [ ] **5 a 10 débitos reais** mostrando como aparecem **boleto, Pix e TED**: `codigoHistorico`, `textoDescricaoSubHistorico`,
         `textoInformacaoComplementar`, se a contrapartida (CPF/CNPJ) vem preenchida e se `textoIdentificadorUnicoTransacao` vem preenchido;
   - [ ] se o **lançamento do dia** aparece como `1` (contabilizado) ou `3` (em processamento) e **com que atraso**.
   Para mascarar a resposta bruta do extrato antes de compartilhar: `node scripts/mascarar-extrato-bb.mjs resposta.json > mascarado.json`
   (lista de permissão: só códigos, tipo, sinal e datas ficam em claro; confira a saída antes de colar).
4. Com isso, preencha `SYSCOR_CONCILIACAO_CODIGOS_HISTORICO` (os `codigoHistorico` que significam pagamento) e ajuste os reforços.
   Enquanto essa lista estiver vazia, o reforço fraco nunca dispara: a baixa automática só acontece com CPF/CNPJ ou nome do
   fornecedor batendo; o resto vira `AMBIGUO` para confirmar com 1 clique (comportamento seguro).
5. Faça um pagamento de teste de valor pequeno, cadastre a conta no SYSCOR com o **CPF/CNPJ do fornecedor** e confira que ela baixa
   sozinha e que o **comprovante em PDF** sai com a origem "Conciliação automática com extrato do Banco do Brasil".

## 4. Ligar em produção

- [ ] Trocar as variáveis para as credenciais, a chave e os hosts de **produção** (a chave e o client id/secret de produção são
      outros) e confirmar o certificado A1 válido.
- [ ] `BB_EXTRATO_ENABLED=true` e reiniciar. O card "canal bancário" da tela de contas a pagar passa de `AGUARDANDO` para `CONECTADO`.
- [ ] Acompanhar os primeiros dias: lançamentos `AMBIGUO` e `SEM_CORRESPONDENCIA` (tarifa, IOF, transferência) ficam na fila;
      use **Vincular** ou **Ignorar** (com motivo). Conta baixada errada: **Reabrir** (o lançamento volta à fila e não casa de novo sozinho).
- [ ] Renovar o certificado A1 antes de vencer (o canal vira `COM_ERRO` quando a sincronização para de funcionar).

## 5. O que o sistema nunca faz

- Não paga nada: o pagamento continua no Internet Banking/app do BB. O SYSCOR só **lê** o extrato.
- Não grava credencial, certificado, extrato real nem comprovante real no repositório, e não registra URL, chave de aplicação,
  token, agência ou conta em log nem em mensagem de erro.
- O comprovante em PDF **não substitui** o comprovante de autenticação emitido pelo banco.
