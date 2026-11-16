package com.vivo4redes.syscor.financeiro.extrato;

import com.vivo4redes.syscor.financeiro.config.BbExtratoProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * O cliente do extrato só existe com {@code syscor.financeiro.bb-extrato.enabled=true}: desligado (padrão), nenhum
 * bean é criado e nada é chamado. O cliente real cria o contexto mTLS só na primeira chamada, então um keystore
 * ausente ou errado vira erro de sincronização, nunca falha de inicialização.
 */
@Configuration
public class ExtratoBancarioConfig {

    @Bean
    @ConditionalOnProperty(prefix = "syscor.financeiro.bb-extrato", name = "enabled", havingValue = "true")
    ExtratoBancarioClient extratoBancarioClient(BbExtratoProperties props, BbExtratoMapper mapper,
                                                Environment ambiente) {
        if (props.getModo() == BbExtratoProperties.Modo.FAKE) {
            if (!ambiente.acceptsProfiles(Profiles.of("dev", "test"))) {
                throw new IllegalStateException(
                        "BB_EXTRATO_MODO=FAKE só é permitido nos profiles dev ou test. "
                                + "Em outro ambiente use BB_EXTRATO_MODO=REAL ou desligue BB_EXTRATO_ENABLED.");
            }
            return new ExtratoBancarioFake(props, mapper);
        }
        return new BancoDoBrasilExtratoClient(props, mapper);
    }
}
