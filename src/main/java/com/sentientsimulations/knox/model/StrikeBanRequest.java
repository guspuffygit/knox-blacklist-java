package com.sentientsimulations.knox.model;

public record StrikeBanRequest(String serverId, Reason reason, String username, String notes) {
    public StrikeBanRequest(String serverId, Reason reason) {
        this(serverId, reason, null, null);
    }
}
