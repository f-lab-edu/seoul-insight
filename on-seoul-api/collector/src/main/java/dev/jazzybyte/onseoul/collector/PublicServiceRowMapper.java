package dev.jazzybyte.onseoul.collector;

import dev.jazzybyte.onseoul.collector.dto.PublicServiceRow;
import dev.jazzybyte.onseoul.domain.PublicServiceReservation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;

/**
 * 서울시 Open API 응답 row({@link PublicServiceRow})를
 * JPA 엔티티({@link PublicServiceReservation})로 변환한다.
 *
 * <ul>
 *   <li>날짜 포맷: {@code "yyyy-MM-dd HH:mm:ss"} + 선택적 소수점 초 ({@code .0} 등)</li>
 *   <li>이용 시간: {@code "HH:mm"} 포맷의 V_MIN / V_MAX</li>
 *   <li>좌표: 빈 문자열·null → {@code null} (Geocoding fallback 대상)</li>
 *   <li>취소 기준일: 숫자 문자열 → {@code Short}, 파싱 실패 시 {@code null}</li>
 * </ul>
 */
@Slf4j
@Component
public class PublicServiceRowMapper {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
            .optionalEnd()
            .toFormatter();

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("HH:mm");

    public PublicServiceReservation toEntity(PublicServiceRow row) {
        return PublicServiceReservation.builder()
                .serviceId(row.getSvcid())
                .serviceGubun(row.getGubun())
                .maxClassName(row.getMaxclassnm())
                .minClassName(row.getMinclassnm())
                .serviceName(row.getSvcnm())
                .serviceStatus(row.getSvcstatnm())
                .paymentType(row.getPayatnm())
                .targetInfo(trimToNull(row.getUsetgtinfo()))
                .serviceUrl(trimToNull(row.getSvcurl()))
                .imageUrl(trimToNull(row.getImgurl()))
                .detailContent(row.getDtlcont())
                .telNo(trimToNull(row.getTelno()))
                .placeName(row.getPlacenm())
                .areaName(row.getAreanm())
                .coordX(parseBigDecimal(row.getX()))
                .coordY(parseBigDecimal(row.getY()))
                .serviceOpenStartDt(parseDateTime(row.getSvcopnbgndt(), "SVCOPNBGNDT", row.getSvcid()))
                .serviceOpenEndDt(parseDateTime(row.getSvcopnenddt(), "SVCOPNENDDT", row.getSvcid()))
                .receiptStartDt(parseDateTime(row.getRcptbgndt(), "RCPTBGNDT", row.getSvcid()))
                .receiptEndDt(parseDateTime(row.getRcptenddt(), "RCPTENDDT", row.getSvcid()))
                .useTimeStart(parseTime(row.getVMin(), "V_MIN", row.getSvcid()))
                .useTimeEnd(parseTime(row.getVMax(), "V_MAX", row.getSvcid()))
                .cancelStdType(trimToNull(row.getRevstddaynm()))
                .cancelStdDays(parseShort(row.getRevstdday(), "REVSTDDAY", row.getSvcid()))
                .build();
    }

    private LocalDateTime parseDateTime(String value, String fieldName, String svcid) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), DATE_TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("날짜 파싱 실패 — svcid={}, field={}, value={}", svcid, fieldName, value);
            return null;
        }
    }

    private LocalTime parseTime(String value, String fieldName, String svcid) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim(), TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("시간 파싱 실패 — svcid={}, field={}, value={}", svcid, fieldName, value);
            return null;
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Short parseShort(String value, String fieldName, String svcid) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Short.parseShort(value.trim());
        } catch (NumberFormatException e) {
            log.warn("숫자 파싱 실패 — svcid={}, field={}, value={}", svcid, fieldName, value);
            return null;
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
