package dev.jazzybyte.onseoul.adapter.out.aiservice;

import reactor.core.publisher.Flux;

public interface AiServicePort {

    /**
     * AI 서비스 /chat/stream을 호출하고 SSE 이벤트의 data 값을 Flux<String>으로 반환한다.
     */
    Flux<String> stream(String question, Long roomId);
}
