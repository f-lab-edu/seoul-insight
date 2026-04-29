package dev.jazzybyte.onseoul.application.service;

import dev.jazzybyte.onseoul.domain.port.in.QueryAndStreamUseCase;
import dev.jazzybyte.onseoul.domain.port.in.SendQueryCommand;
import dev.jazzybyte.onseoul.domain.port.in.SendQueryUseCase;
import dev.jazzybyte.onseoul.domain.port.out.AiServiceStreamPort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

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
        AtomicReference<StringBuilder> buffer = new AtomicReference<>(new StringBuilder());

        return aiServiceStreamPort.stream(command.question(), roomId)
                .doOnNext(token -> buffer.get().append(token))
                .doOnComplete(() -> sendQueryUseCase.saveAnswer(roomId, buffer.get().toString()));
    }
}
