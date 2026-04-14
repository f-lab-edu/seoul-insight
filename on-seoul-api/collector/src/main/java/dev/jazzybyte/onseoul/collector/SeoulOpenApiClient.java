package dev.jazzybyte.onseoul.collector;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import dev.jazzybyte.onseoul.collector.config.SeoulApiProperties;
import dev.jazzybyte.onseoul.collector.dto.PublicServiceRow;
import dev.jazzybyte.onseoul.collector.dto.SeoulApiResponse;
import dev.jazzybyte.onseoul.collector.exception.SeoulApiException;
import dev.jazzybyte.onseoul.collector.exception.SeoulApiServerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SeoulOpenApiClient {

    private final WebClient webClient;
    private final SeoulApiProperties properties;
    private final ObjectMapper objectMapper;
    private final Retry retrySpec;

    public SeoulOpenApiClient(WebClient seoulWebClient,
                              SeoulApiProperties properties,
                              ObjectMapper objectMapper) {
        this.webClient = seoulWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.retrySpec = Retry.backoff(properties.getMaxRetries(), Duration.ofSeconds(1))
                              .maxBackoff(Duration.ofSeconds(10))
                              .filter(ex -> ex instanceof SeoulApiServerException);
    }

    // 테스트용 생성자 — retrySpec 주입
    SeoulOpenApiClient(WebClient seoulWebClient,
                       SeoulApiProperties properties,
                       ObjectMapper objectMapper,
                       Retry retrySpec) {
        this.webClient = seoulWebClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.retrySpec = retrySpec;
    }

    /**
     * 주어진 서비스명에 해당하는 전체 데이터를 페이지네이션으로 수집한다.
     *
     * @param serviceName 서울시 Open API 서비스명 (예: ListPublicReservationCulture)
     * @return 수집된 전체 row 목록
     */
    public List<PublicServiceRow> fetchAll(String serviceName) {
        int pageSize = properties.getPageSize();

        SeoulApiResponse firstPage = fetchPage(serviceName, 1, pageSize);
        int totalCount = firstPage.getListTotalCount();

        log.info("서울시 Open API 수집 시작: serviceName={}, 전체={}건", serviceName, totalCount);

        List<PublicServiceRow> result = new ArrayList<>(totalCount);
        result.addAll(firstPage.getRows());

        for (int start = pageSize + 1; start <= totalCount; start += pageSize) {
            SeoulApiResponse page = fetchPage(serviceName, start, Math.min(start + pageSize - 1, totalCount));
            result.addAll(page.getRows());
            log.debug("페이지 수집 완료: {}-{} / 전체 {}", start, start + page.getRows().size() - 1, totalCount);
        }

        return result;
    }

    SeoulApiResponse fetchPage(String serviceName, int startIndex, int endIndex) {
        return webClient.get()
                .uri("/{key}/json/{serviceName}/{start}/{end}/",
                     properties.getKey(), serviceName, startIndex, endIndex)
                .retrieve()
                .onStatus(status -> status.is5xxServerError(),
                          resp -> Mono.error(
                                  new SeoulApiServerException("서울 API 서버 오류: " + resp.statusCode())))
                .onStatus(status -> status.is4xxClientError(),
                          resp -> Mono.error(
                                  new SeoulApiException("서울 API 클라이언트 오류: " + resp.statusCode())))
                .bodyToMono(String.class)
                .map(body -> parseResponse(body, serviceName))
                .retryWhen(retrySpec)
                .block();
    }

    private SeoulApiResponse parseResponse(String body, String serviceName) {
        JsonNode root = objectMapper.readTree(body);
        JsonNode inner = root.get(serviceName);
        if (inner == null || inner.isNull()) {
            throw new SeoulApiException("API 응답에서 서비스 키를 찾을 수 없음: " + serviceName);
        }
        SeoulApiResponse response = objectMapper.treeToValue(inner, SeoulApiResponse.class);
        if (!response.isSuccess()) {
            throw new SeoulApiException("API 오류 코드: " + response.getResult().getCode()
                                        + " / " + response.getResult().getMessage());
        }
        return response;
    }
}
