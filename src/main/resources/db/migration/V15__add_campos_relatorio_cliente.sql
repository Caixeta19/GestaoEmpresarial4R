-- Migration V15: Adiciona campos de endereço e RG/IE do relatório de clientes
ALTER TABLE clientes
    ADD COLUMN IF NOT EXISTS rg_ie VARCHAR(30),
    ADD COLUMN IF NOT EXISTS uf VARCHAR(2),
    ADD COLUMN IF NOT EXISTS cidade VARCHAR(100),
    ADD COLUMN IF NOT EXISTS endereco VARCHAR(255),
    ADD COLUMN IF NOT EXISTS complemento VARCHAR(100),
    ADD COLUMN IF NOT EXISTS bairro VARCHAR(100),
    ADD COLUMN IF NOT EXISTS cep VARCHAR(10);

-- Expande o tamanho de cpf_cnpj para até 20 caracteres caso contenha formatação
ALTER TABLE clientes ALTER COLUMN cpf_cnpj TYPE VARCHAR(20);