package com.vivo4redes.syscor.service;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gera o número público da venda (7 dígitos, ex: 1000000) a partir da
 * sequence seq_numero_venda (ver V3). Fica separado do @Id/PK interno para
 * não expor a chave técnica da tabela e para não competir com outras
 * inserções na tabela de vendas.
 */
@Component
public class NumeroVendaGenerator {

    private final EntityManager entityManager;

    public NumeroVendaGenerator(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long gerar() {
        return ((Number) entityManager
                .createNativeQuery("SELECT nextval('seq_numero_venda')")
                .getSingleResult())
                .longValue();
    }
}