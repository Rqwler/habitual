package com.habittracker.habit.dto;

import com.habittracker.habit.FrequencyType;
import java.time.Instant;
import java.util.List;

public record HabitResponse(
    Long id,
    String name,
    String description,
    String category,
    FrequencyType frequencyType,
    Integer timesPerWeek,
    List<Integer> weekdays,
    int priority,
    boolean archived,
    Instant createdAt,
    int currentStreak,
    int longestStreak,
    String streakUnit,
    boolean scheduledToday,
    boolean completedToday,
    int completionRate30d,
    int weekCompleted,
    int weekTarget) {}
