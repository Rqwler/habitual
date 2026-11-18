package com.habittracker.event;

import java.time.LocalDate;

/** Published after a new check-in is committed. Extension point for badges and reminders. */
public record HabitCompletedEvent(Long habitId, Long userId, LocalDate date) {}
