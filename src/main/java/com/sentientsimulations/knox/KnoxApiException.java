package com.sentientsimulations.knox;

import lombok.Getter;

@Getter
public class KnoxApiException extends KnoxException {
    private final int statusCode;
    private final String body;

    public KnoxApiException(int statusCode, String body) {
        super("Knox API returned " + statusCode + ": " + body);
        this.statusCode = statusCode;
        this.body = body;
    }
}
