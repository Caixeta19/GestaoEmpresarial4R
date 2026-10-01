ALTER TABLE clientes
    ADD COLUMN consentimento_marketing BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN versao_termo_consentimento VARCHAR(50),
    ADD COLUMN data_consentimento TIMESTAMP WITH TIME ZONE;