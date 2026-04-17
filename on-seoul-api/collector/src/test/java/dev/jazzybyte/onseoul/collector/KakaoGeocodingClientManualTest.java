package dev.jazzybyte.onseoul.collector;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jazzybyte.onseoul.collector.config.KakaoApiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카카오 키워드 검색 API 실제 호출 테스트.
 *
 * <p>{@code KAKAO_API_KEY} 환경변수가 설정된 상태에서 {@code @Disabled}를 제거하고 실행한다.</p>
 */
@Disabled("카카오 API 실제 호출 — 환경변수 KAKAO_API_KEY 설정 후 @Disabled 제거하여 수동 실행")
class KakaoGeocodingClientManualTest {

    private KakaoGeocodingClient client;

    @BeforeEach
    void setUp() {
        String apiKey = System.getenv("KAKAO_API_KEY");
        assertThat(apiKey)
                .as("KAKAO_API_KEY 환경변수가 설정되어 있어야 합니다")
                .isNotBlank();

        KakaoApiProperties properties = new KakaoApiProperties();
        properties.setKey(apiKey);
        // baseUrl 기본값 "https://dapi.kakao.com" 사용

        WebClient kakaoWebClient = WebClient.builder()
                .baseUrl(properties.getBaseUrl())
                .defaultHeader("Authorization", "KakaoAK " + properties.getKey())
                .build();

        client = new KakaoGeocodingClient(kakaoWebClient, new ObjectMapper());
    }

    @Test
    @DisplayName("'서울특별시 산악문화체험센터' 검색 시 좌표를 반환한다")
    void search_real_place() {
        Optional<BigDecimal[]> result = client.search("서울특별시 산악문화체험센터");

        assertThat(result).isPresent();
        BigDecimal x = result.get()[0];
        BigDecimal y = result.get()[1];

        System.out.printf("장소: 서울특별시 산악문화체험센터%n  x(경도): %s%n  y(위도): %s%n", x, y);

        // 서울 시내 경도/위도 범위 검증
        assertThat(x).isBetween(new BigDecimal("126.7"), new BigDecimal("127.2"));
        assertThat(y).isBetween(new BigDecimal("37.4"), new BigDecimal("37.7"));
    }

    @Test
    @DisplayName("존재하지 않는 장소명은 Optional.empty()를 반환한다")
    void search_nonexistent_place_returns_empty() {
        Optional<BigDecimal[]> result = client.search("이런장소는절대없을거야_xQzPf9");

        assertThat(result).isEmpty();
        System.out.println("존재하지 않는 장소 검색 → Optional.empty() 확인");
    }
}
