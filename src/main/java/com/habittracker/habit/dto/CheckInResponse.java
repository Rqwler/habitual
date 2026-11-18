package com.habittracker.habit.dto;

import java.time.LocalDate;

public record CheckInResponse(
    Long habitId,
    LocalDate date,
    boolean completed,
    int currentStreak,
    int longestStreak,
    String streakUnit,
    int weekCompleted,
    int weekTarget,
    String message) {}
