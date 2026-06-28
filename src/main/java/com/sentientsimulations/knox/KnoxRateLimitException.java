package com.sentientsimulations.knox;

import lombok.Getter;

@Getter
public class KnoxRateLimitException extends KnoxApiException {
    private final long retryAfterSeconds;

    public KnoxRateLimitException(String body, long retryAfterSeconds) {
        super(429, body);
        this.retryAfterSeconds = retryAfterSeconds;
    }
}
