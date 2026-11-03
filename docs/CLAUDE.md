# CLAUDE.md — GestaoEmpresarial4R (syscor)

## Contexto
Backend do ERP da 4R Redes / vivo4redes (varejo Vivo, 85 lojas).
A venda é a entidade central: estoque, documental, financeiro e dashboard derivam dela.
Especificação completa em docs/ESPECIFICACAO.md — leia antes de qualquer mudança.
Frontend React/Vite fica em outro repositório (syscor-frontend): não quebre contratos de API sem avisar.

## Stack
Java 21, Spring Boot 3.3.4, PostgreSQL 16, Flyway, RabbitMQ, Spring Security + JWT (jjwt),
Lombok, springdoc-openapi, JUnit 5 + Mockito + Testcontainers. Build: ./mvnw (Windows: mvnw.cmd).
Context-path da API: /api. Perfil padrão: dev.

## Estrutura
Pacote base com.vivo4redes.syscor, organizado por módulo de domínio:
security, venda, estoque, gestao (documental), financeiro, mailing, dashboard, exception, util.
Dentro de cada módulo: controller / service / repository / model / dto (request, response) / enums.

## Regras de código
- Controller só traduz HTTP; regra de negócio fica no Service; máquina de estados fica no enum (StatusVenda).
- Nunca exponha Entity na API: use ResponseDTO com método estático from().
- Erro de negócio = exceção que estende BusinessException (vira 422 no GlobalExceptionHandler).
- Schema só via Flyway: nunca edite V1..V20; crie a próxima versão (V21+). ddl-auto continua validate.
- Literais de enum Java e CHECK do banco precisam ser idênticos.
- Toda baixa/reserva de serial passa por EstoqueGeralService (SELECT ... FOR UPDATE + @Version).
- Valores monetários em BigDecimal, comparados com compareTo.
- Segredos só por variável de ambiente; nunca commitar .env, tokens ou chaves.
- Nomes de domínio, mensagens de erro e commits em português.

## Decisões já tomadas
- Status de avaria do estoque: DEFEITO (não AVARIADO).
- Carrinho: item adicionado fica RESERVADO; vira VENDIDO quando a venda é CONCLUIDA;
  volta a DISPONIVEL ao remover o item ou cancelar a venda.
- Preço: vale a tabela da Telefônica importada mais recente (vigente). O preço do item é
  resolvido no backend por código/modelo + oferta; o front não define o valor unitário.
- Webhook do Banco do Brasil autentica por mTLS com certificado A1 (não por segredo/hmac).
- Perfis de acesso = cargos da tabela usuarios (UsuarioDetailsService já gera ROLE_<CARGO>).
- Metas: uma por nível da hierarquia de cargos e por mês.

## Como trabalhar
1. Antes de codar, mostre o plano: arquivos que vai mudar e por quê. Espere aprovação.
2. Uma fase/tarefa por vez, seguindo a seção 10 e 10.1 de docs/ESPECIFICACAO.md.
3. Toda regra nova ou bug corrigido ganha teste (Mockito no service; Testcontainers para repositório).
4. Rode ./mvnw verify antes de concluir. Não conclua com teste falhando.
5. Commits pequenos no padrão feat: / fix: / refactor: / test: / docs:.
6. Ao mudar regra, endpoint ou tabela, atualize docs/ESPECIFICACAO.md e README.md.
7. Se algo da especificação estiver ambíguo ou contradizer o código, pergunte antes de decidir.