package dev.jazzybyte.onseoul.collector.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "seoul.api")
public class SeoulApiProperties {

    private String key;
    private String baseUrl = "http://openapi.seoul.go.kr:8088";
    private int pageSize = 200;
    private int maxRetries = 3;
}
