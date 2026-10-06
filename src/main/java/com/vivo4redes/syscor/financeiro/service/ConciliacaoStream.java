package com.vivo4redes.syscor.financeiro.service;

import com.vivo4redes.syscor.financeiro.dto.response.ConciliacaoEventoDTO;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Tempo real para a tela (SSE). Com várias instâncias, troque por Redis pub/sub ou LISTEN/NOTIFY do PostgreSQL. */
@Component
public class ConciliacaoStream {

    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public SseEmitter assinar() {
        SseEmitter e = new SseEmitter(0L);
        emitters.add(e);
        e.onCompletion(() -> emitters.remove(e));
        e.onTimeout(() -> emitters.remove(e));
        e.onError(x -> emitters.remove(e));
        return e;
    }

    public void publicar(ConciliacaoEventoDTO dto) {
        for (SseEmitter e : emitters) {
            try {
                e.send(SseEmitter.event().name("conciliacao").data(dto));
            } catch (Exception ex) {
                emitters.remove(e);
            }
        }
    }
}