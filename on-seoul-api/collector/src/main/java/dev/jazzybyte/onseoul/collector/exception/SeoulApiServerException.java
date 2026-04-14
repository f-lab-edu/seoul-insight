package dev.jazzybyte.onseoul.collector.exception;

/** 5xx 서버 오류 — 재시도 대상 */
public class SeoulApiServerException extends SeoulApiException {

    public SeoulApiServerException(String message) {
        super(message);
    }
}
