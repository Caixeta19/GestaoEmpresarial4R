# Caixa: fechamento com valor contado digitado (diferença real)

Repos: GestaoEmpresarial4R e FrontendGestaoEmpresarial4R (caixa diário já em main: PRs de abertura obrigatória e permissões).

## Problema

Em `src/components/caixa/PainelFechamento.jsx` o campo "valor contado" do dinheiro começa preenchido com o valor esperado
(`useState(reais(dinheiro?.contabilizadoEsperado))`). O gerente só clica em Fechar, contado = esperado e a diferença sai sempre
0,00 (ex.: caixa CE - CANINDE, abertura R$ 100,00, contabilizado R$ 2.099,00, fechamento R$ 2.099,00, diferença R$ 0,00).
A conferência perde o sentido: ninguém precisa contar a gaveta e o sistema nunca acusa falta ou sobra.

Fórmula que continua valendo: diferença = contado − esperado (esperado do dinheiro = abertura + movimento + entradas − saídas).
Só o DINHEIRO é contado; PIX, Vivo Pay, cartões e boleto liquidam na conciliação e ficam com diferença 0,00.

## Frontend

1. O campo de contado do dinheiro começa **vazio**, com placeholder "Informe o valor contado na gaveta". O botão **Fechar caixa**
   fica desabilitado até o campo ser preenchido; 0,00 é aceito se o gerente digitou (distinguir vazio de zero).
   Aceitar vírgula e ponto, 2 casas, não aceitar negativo.
2. **Fechamento cego:** enquanto o contado não for digitado, não mostrar o esperado do dinheiro na tela (mostrar "—"). Ao digitar,
   mostrar esperado, contado e diferença; a diferença em destaque: verde com 0,00, laranja/vermelho quando ≠ 0,00 (com sinal: sobra +,
   falta −).
3. Diferença ≠ 0: a observação continua obrigatória (mín. 10 caracteres), com a mensagem que já existe. Mostrar um texto de
   confirmação antes de fechar: "Fechar com diferença de R$ X?".
4. Nada de pré-preencher o contado ao reabrir o painel, ao trocar de caixa ou ao recarregar; sempre começa vazio.
5. Manter o visual atual (variáveis CSS e componentes do caixa).

## Backend

1. `CaixaSessaoService.fechar`: confirmar que exige `valorConferido` para a forma DINHEIRO e que rejeita ausente (422), negativo (422) e
   duplicado; nenhuma regra pode assumir o esperado como padrão. Corrigir se houver.
2. A resposta do detalhe de um caixa **aberto** não deve expor o esperado do dinheiro a quem vai fechar (campo `contabilizadoEsperado`
   só no detalhe de caixa fechado, ou só para os perfis que conferem: financeiro, gerente de área e admin). Gerente da loja vê o
   esperado somente depois do fechamento. Ajustar o DTO sem quebrar a lista de caixas abertos nem a Conferência.
3. Testes de integração (dados sintéticos):
    - fechar sem valor de dinheiro → 422;
    - fechar com valor igual ao esperado → diferença 0,00, sem exigir observação;
    - fechar com valor menor → diferença negativa gravada e observação exigida (422 sem ela);
    - fechar com valor maior → diferença positiva gravada;
    - gerente da loja não recebe o esperado do caixa aberto; financeiro recebe.

## Fluxo

Plan mode → aprovar → `mvnw.cmd verify` / `npm run build` → commit → PR para main (back e front separados). Sem dados reais em teste.
Depois de mergeado, reabrir o caixa de teste de CE - CANINDE e fechar de novo digitando o valor contado, para validar a diferença.