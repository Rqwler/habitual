package com.habittracker.habit.frequency;

import com.habittracker.habit.FrequencyType;
import com.habittracker.habit.Habit;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Extension point for habit schedules. Adding a new frequency means adding a new implementation of
 * this interface, not changing the streak, calendar or statistics code.
 */
public interface HabitFrequencyStrategy {

  FrequencyType type();

  boolean isScheduledOn(Habit habit, LocalDate date);

  List<LocalDate> scheduledDates(Habit habit, LocalDate startInclusive, LocalDate endInclusive);

  /**
   * Number of check-ins a perfect user would have in the period. Weekly habits use a flexible
   * schedule, so the target is capped by the number of available days.
   */
  int expectedCompletions(
      Habit habit, LocalDate startInclusive, LocalDate endInclusive, LocalDate referenceDate);

  StreakResult calculateStreak(Habit habit, Set<LocalDate> completedDates, LocalDate referenceDate);

  default List<LocalDate> everyDate(LocalDate startInclusive, LocalDate endInclusive) {
    List<LocalDate> dates = new ArrayList<>();
    LocalDate cursor = startInclusive;
    while (!cursor.isAfter(endInclusive)) {
      dates.add(cursor);
      cursor = cursor.plusDays(1);
    }
    return dates;
  }

  default LocalDate capAtReference(LocalDate endInclusive, LocalDate referenceDate) {
    return endInclusive.isAfter(referenceDate) ? referenceDate : endInclusive;
  }

  default LocalDate effectiveStart(Habit habit, LocalDate startInclusive) {
    if (habit.getCreatedAt() == null) {
      return startInclusive;
    }
    LocalDate created = habit.getCreatedAt().atZone(ZoneOffset.UTC).toLocalDate();
    return startInclusive.isBefore(created) ? created : startInclusive;
  }
}
