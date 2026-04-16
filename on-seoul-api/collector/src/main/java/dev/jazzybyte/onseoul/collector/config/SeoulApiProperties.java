package dev.jazzybyte.onseoul.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@Getter
@Setter
@ConfigurationProperties(prefix = "seoul.api")
public class SeoulApiProperties {

    private String key;
    private String baseUrl = "http://openapi.seoul.go.kr:8088";
    private int pageSize = 200;
    private int maxRetries = 3;
    /** TCP 연결 수립 타임아웃 (밀리초) */
    private int connectTimeoutMs = 10_000; // 기본값 10초
    /** HTTP 응답 수신 타임아웃 (초) */
    private int responseTimeoutSeconds = 30; // 기본값 30초

    public Duration getBlockTimeout() {
        return Duration.ofSeconds((responseTimeoutSeconds + 10L) * (maxRetries + 1));
    }
}
