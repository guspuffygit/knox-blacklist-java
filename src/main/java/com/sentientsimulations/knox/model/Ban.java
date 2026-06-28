package com.sentientsimulations.knox.model;

public record Ban(
        String id,
        String steamId,
        String serverId,
        String reason,
        String notes,
        String createdAt,
        String expiresAt) {}
