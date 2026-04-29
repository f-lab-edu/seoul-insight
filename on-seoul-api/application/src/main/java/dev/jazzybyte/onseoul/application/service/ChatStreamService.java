package dev.jazzybyte.onseoul.application.service;

import dev.jazzybyte.onseoul.domain.port.in.QueryAndStreamUseCase;
import dev.jazzybyte.onseoul.domain.port.in.SendQueryCommand;
import dev.jazzybyte.onseoul.domain.port.in.SendQueryUseCase;
import dev.jazzybyte.onseoul.domain.port.out.AiServiceStreamPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

@Service
public class ChatStreamService implements QueryAndStreamUseCase {

    private final SendQueryUseCase sendQueryUseCase;
    private final AiServiceStreamPort aiServiceStreamPort;

    public ChatStreamService(final SendQueryUseCase sendQueryUseCase,
                             final AiServiceStreamPort aiServiceStreamPort) {
        this.sendQueryUseCase = sendQueryUseCase;
        this.aiServiceStreamPort = aiServiceStreamPort;
    }

    @Override
    public Flux<String> streamAndSave(SendQueryCommand command) {
        Long roomId = sendQueryUseCase.prepare(command);
        StringBuilder buffer = new StringBuilder();

        return aiServiceStreamPort.stream(command.question(), roomId)
                .publishOn(Schedulers.boundedElastic())  // Netty 이벤트 루프 → boundedElastic 전환(블로킹 작업 허용 및 직렬 실행 보장)
                .doOnNext(buffer::append)                // 단일 스레드 직렬 실행 → StringBuilder 안전
                .doOnComplete(() -> sendQueryUseCase.saveAnswer(roomId, buffer.toString())); // JPA 블로킹 OK
    }
}
