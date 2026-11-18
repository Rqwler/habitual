package com.habittracker.stats.dto;

import java.time.LocalDate;

public record DashboardResponse(
    LocalDate date,
    PeriodSummary today,
    PeriodSummary week,
    PeriodSummary month,
    int activeHabits,
    int bestCurrentStreak,
    String bestStreakUnit,
    long totalCheckIns) {}
