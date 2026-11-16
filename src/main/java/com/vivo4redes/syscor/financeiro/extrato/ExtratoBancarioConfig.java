package com.vivo4redes.syscor.financeiro.extrato;

import com.vivo4redes.syscor.financeiro.config.BbExtratoProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * O cliente do extrato só existe com {@code syscor.financeiro.bb-extrato.enabled=true}: desligado (padrão), nenhum
 * bean é criado e nada é chamado. O cliente real cria o contexto mTLS só na primeira chamada, então um keystore
 * ausente ou errado vira erro de sincronização, nunca falha de inicialização.
 */
@Configuration
public class ExtratoBancarioConfig {

    @Bean
    @ConditionalOnProperty(prefix = "syscor.financeiro.bb-extrato", name = "enabled", havingValue = "true")
    ExtratoBancarioClient extratoBancarioClient(BbExtratoProperties props, BbExtratoMapper mapper) {
        return props.getModo() == BbExtratoProperties.Modo.FAKE
                ? new ExtratoBancarioFake(props, mapper)
                : new BancoDoBrasilExtratoClient(props, mapper);
    }
}
