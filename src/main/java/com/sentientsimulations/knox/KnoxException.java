package com.sentientsimulations.knox;

public class KnoxException extends RuntimeException {
    public KnoxException(String message) {
        super(message);
    }

    public KnoxException(String message, Throwable cause) {
        super(message, cause);
    }
}
