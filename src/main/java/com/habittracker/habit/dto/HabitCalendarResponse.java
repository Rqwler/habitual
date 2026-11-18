package com.habittracker.habit.dto;

import com.habittracker.habit.FrequencyType;
import java.util.List;

public record HabitCalendarResponse(
    Long habitId,
    String habitName,
    FrequencyType frequencyType,
    int year,
    int month,
    List<CalendarDay> days,
    int completedCount,
    int targetCount) {}
