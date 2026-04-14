package dev.jazzybyte.onseoul.collector.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 서울시 공공서비스 예약 Open API 응답의 row 항목.
 * 필드명은 API 원문 그대로 사용한다 (대문자 + 약어).
 * 날짜/시간/좌표는 모두 String으로 수신하며 변환은 변환기(Phase 5)에서 처리한다.
 */
@Getter
@NoArgsConstructor
public class PublicServiceRow {

    @JsonProperty("GUBUN")
    private String gubun;               // 서비스 구분 (체육, 문화 등)

    @JsonProperty("SVCID")
    private String svcid;               // 서비스 ID (고유키)

    @JsonProperty("MAXCLASSNM")
    private String maxclassnm;          // 대분류명

    @JsonProperty("MINCLASSNM")
    private String minclassnm;          // 소분류명

    @JsonProperty("SVCSTATNM")
    private String svcstatnm;           // 서비스 상태명 (접수중, 마감 등)

    @JsonProperty("SVCNM")
    private String svcnm;               // 서비스명

    @JsonProperty("PAYATNM")
    private String payatnm;             // 결제 방법

    @JsonProperty("PLACENM")
    private String placenm;             // 장소명

    @JsonProperty("USETGTINFO")
    private String usetgtinfo;          // 이용 대상 정보

    @JsonProperty("SVCURL")
    private String svcurl;              // 서비스 URL

    @JsonProperty("X")
    private String x;                   // X 좌표 (경도)

    @JsonProperty("Y")
    private String y;                   // Y 좌표 (위도)

    @JsonProperty("SVCOPNBGNDT")
    private String svcopnbgndt;         // 서비스 개시 시작일시

    @JsonProperty("SVCOPNENDDT")
    private String svcopnenddt;         // 서비스 개시 종료일시

    @JsonProperty("RCPTBGNDT")
    private String rcptbgndt;           // 접수 시작일시

    @JsonProperty("RCPTENDDT")
    private String rcptenddt;           // 접수 종료일시

    @JsonProperty("AREANM")
    private String areanm;              // 자치구명

    @JsonProperty("IMGURL")
    private String imgurl;              // 이미지 URL

    @JsonProperty("DTLCONT")
    private String dtlcont;             // 상세 내용 (HTML 포함 가능, 원문 저장)

    @JsonProperty("TELNO")
    private String telno;               // 전화번호

    @JsonProperty("USETMINFO")
    private String usetminfo;           // 이용 시간 (예: "09:00~18:00", 파싱 필요)

    @JsonProperty("REVSTDDAYNM")
    private String revstddaynm;         // 취소 기준 유형명

    @JsonProperty("REVSTDDAY")
    private String revstdday;           // 취소 기준일 수
}
