package com.sentientsimulations.knox.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Reason {
    HARASSMENT,
    RACISM,
    SPAM,
    DISRUPTIVE,
    CHEATING,
    EXPLOITS,
    GRIEFING,
    HACKING,
    DOXXING,
    THREATS;

    @JsonValue
    public String value() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static Reason fromValue(String s) {
        return s == null ? null : Reason.valueOf(s.toUpperCase());
    }
}
