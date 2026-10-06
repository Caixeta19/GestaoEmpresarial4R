package com.vivo4redes.syscor.financeiro.webhook;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class BancoRegistry {
    private final Map<String, BancoAdapter> porCodigo;

    public BancoRegistry(List<BancoAdapter> adapters) {
        this.porCodigo = adapters.stream().collect(Collectors.toMap(BancoAdapter::codigo, Function.identity()));
    }

    public BancoAdapter get(String codigo) {
        BancoAdapter a = porCodigo.get(codigo);
        if (a == null) throw new IllegalArgumentException("Banco desconhecido: " + codigo);
        return a;
    }
}