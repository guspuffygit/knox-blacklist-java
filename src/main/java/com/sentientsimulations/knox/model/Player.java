package com.sentientsimulations.knox.model;

import java.util.List;

public record Player(
        String steamId,
        Integer threatLevel,
        List<String> ign,
        List<String> discordHandles,
        Boolean bannedByOrg,
        Integer networkBanCount) {}
