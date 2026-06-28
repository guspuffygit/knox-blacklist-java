package com.sentientsimulations.knox.model;

import java.util.List;

public record ReportRequest(
        String serverName,
        Reason reason,
        String notes,
        List<String> ign,
        List<String> discordHandles,
        List<String> discordNames,
        List<String> ips) {}
