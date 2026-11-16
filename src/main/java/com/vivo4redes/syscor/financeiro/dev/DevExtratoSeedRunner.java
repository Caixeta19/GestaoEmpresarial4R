package com.vivo4redes.syscor.financeiro.dev;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Semeia as contas de demonstração do extrato FAKE, só no profile dev e só com o extrato ligado em FAKE. */
@Component
@Profile("dev")
@ConditionalOnProperty(prefix = "syscor.financeiro.bb-extrato", name = "enabled", havingValue = "true")
public class DevExtratoSeedRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DevExtratoSeedRunner.class);

    private final DevExtratoSeedService seed;
    private final com.vivo4redes.syscor.financeiro.config.BbExtratoProperties props;

    public DevExtratoSeedRunner(DevExtratoSeedService seed,
                                com.vivo4redes.syscor.financeiro.config.BbExtratoProperties props) {
        this.seed = seed;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        if (props.getModo() != com.vivo4redes.syscor.financeiro.config.BbExtratoProperties.Modo.FAKE) return;
        int criadas = seed.semear();
        log.info("Seed de dev do extrato FAKE: {} conta(s) de demonstração criada(s).", criadas);
    }
}
