package com.sentientsimulations.knox.model;

public record BanRequest(String serverId, String username) {
    public BanRequest(String serverId) {
        this(serverId, null);
    }
}
