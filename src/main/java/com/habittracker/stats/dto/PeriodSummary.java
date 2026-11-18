package com.habittracker.stats.dto;

import java.time.LocalDate;

public record PeriodSummary(
    LocalDate start, LocalDate end, int completed, int expected, int percentage) {}
