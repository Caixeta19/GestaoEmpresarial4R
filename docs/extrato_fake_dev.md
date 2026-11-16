# Extrato FAKE em dev

Para ver a conciliação do extrato funcionando sem falar com o Banco do Brasil.

## Como ligar
1. Profile `dev` (padrão; é o do `Docker-compose.yml`).
2. Variáveis: `BB_EXTRATO_ENABLED=true` e `BB_EXTRATO_MODO=FAKE`. No compose, ponha-as no `.env`.
3. Suba a aplicação. No startup o seed cria as contas de demonstração (idempotente: prefixo `[DEMO-EXTRATO]` na descrição).

`BB_EXTRATO_MODO=FAKE` só é aceito nos profiles `dev` e `test`. Em qualquer outro a aplicação **não sobe**, com a mensagem
"BB_EXTRATO_MODO=FAKE só é permitido nos profiles dev ou test".

## O que existe
- `src/main/resources/dev/extrato-fake-dev.json`: extrato sintético no formato da API Extratos v2. Cada lançamento traz o campo
  auxiliar `diasAtras`; com `fake-datas-relativas=true` (só no `application-dev.yaml`) o fake reescreve as datas para
  "hoje − diasAtras", então os lançamentos nunca saem da `janela-dias`.
- `DevExtratoSeedService` / `DevExtratoSeedRunner` (profile `dev`): contas a pagar fictícias que casam com o JSON.

| Lançamento | Conta no seed | Resultado esperado |
|---|---|---|
| 1234,50, CNPJ 11111111000111 | 1 conta ABERTO, mesmo valor e CNPJ | Conciliado sozinho |
| 777,77, sem documento | 2 contas ABERTO com 777,77 | Ambíguo (com sugestões) |
| 55,55, sem documento | nenhuma | Sem conta |
| 380,00, CNPJ 22222222000122 | 1 conta de 400,00 com o mesmo CNPJ | Conta DIVERGENTE (lançamento em ambíguos) |
| Crédito de 500,00 | | Ignorado |

Clicar em "Sincronizar agora" de novo não duplica nada (`id_externo` único).
