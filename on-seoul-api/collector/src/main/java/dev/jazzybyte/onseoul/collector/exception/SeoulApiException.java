package dev.jazzybyte.onseoul.collector.exception;

public class SeoulApiException extends RuntimeException {

    public SeoulApiException(String message) {
        super(message);
    }

    public SeoulApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
