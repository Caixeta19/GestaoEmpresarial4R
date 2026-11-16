package com.vivo4redes.syscor.financeiro.extrato;

import com.vivo4redes.syscor.financeiro.config.BbExtratoProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExtratoBancarioConfigTest {

    private final ExtratoBancarioConfig config = new ExtratoBancarioConfig();
    private final BbExtratoMapper mapper = new BbExtratoMapper();

    private BbExtratoProperties fake() {
        BbExtratoProperties props = new BbExtratoProperties();
        props.setModo(BbExtratoProperties.Modo.FAKE);
        return props;
    }

    @Test
    @DisplayName("FAKE fora de dev/test (ex.: prod) falha na inicialização, com mensagem clara")
    void fakeEmProd() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");

        assertThatThrownBy(() -> config.extratoBancarioClient(fake(), mapper, prod))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BB_EXTRATO_MODO=FAKE")
                .hasMessageContaining("dev ou test");
    }

    @Test
    @DisplayName("FAKE em dev e em test é permitido")
    void fakeEmDevETest() {
        for (String profile : new String[]{"dev", "test"}) {
            MockEnvironment env = new MockEnvironment();
            env.setActiveProfiles(profile);
            assertThat(config.extratoBancarioClient(fake(), mapper, env)).isInstanceOf(ExtratoBancarioFake.class);
        }
    }
}
