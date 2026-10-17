package com.ithopeschool.projectbe.domain.focus.dto;

import java.util.Map;

public record LockEventStats(int total, Map<Integer, Long> byHour, Map<String, Long> byApp) {}
