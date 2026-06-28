package com.sentientsimulations.knox.model;

import java.util.List;

public record BanListResponse(List<Ban> data, Integer page, Integer limit, Integer total) {}
