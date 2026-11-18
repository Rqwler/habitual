package com.habittracker.habit;

public record HabitMetrics(
    int currentStreak,
    int longestStreak,
    String streakUnit,
    int completionRate30d,
    int weekCompleted,
    int weekTarget,
    boolean scheduledToday,
    boolean completedToday) {}
