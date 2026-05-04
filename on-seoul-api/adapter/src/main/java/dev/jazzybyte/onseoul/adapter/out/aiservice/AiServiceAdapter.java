package dev.jazzybyte.onseoul.adapter.out.aiservice;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.jazzybyte.onseoul.domain.port.out.AiServiceStreamPort;
import dev.jazzybyte.onseoul.exception.ErrorCode;
import dev.jazzybyte.onseoul.exception.OnSeoulApiException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

import java.time.Duration;

@Component
public class AiServiceAdapter implements AiServiceStreamPort {

    private final WebClient webClient;
    private final AiServiceProperties properties;

    public AiServiceAdapter(@Qualifier("aiServiceWebClient") final WebClient webClient,
                            final AiServiceProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AiChatRequest(
            @JsonProperty("room_id") long roomId,
            @JsonProperty("message_id") long messageId,
            @JsonProperty("message") String message,
            @JsonProperty("lat") Double lat,
            @JsonProperty("lng") Double lng
    ) {}

    @Override
    public Flux<String> stream(String question, long roomId, long messageId, Double lat, Double lng) {
        AiChatRequest body = new AiChatRequest(roomId, messageId, question, lat, lng);
        return webClient.post()
                .uri("/chat/stream")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(body)
                .retrieve()
                .bodyToFlux(new ParameterizedTypeReference<ServerSentEvent<String>>() {})
                .timeout(Duration.ofSeconds(properties.streamTimeoutSeconds()))
                .mapNotNull(ServerSentEvent::data)
                .onErrorMap(e -> !(e instanceof OnSeoulApiException),
                        e -> new OnSeoulApiException(ErrorCode.AI_SERVICE_ERROR,
                                "AI 서비스 스트림 오류: " + e.getMessage(), e));
    }
}
