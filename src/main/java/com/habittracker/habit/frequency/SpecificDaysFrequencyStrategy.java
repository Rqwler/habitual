package com.habittracker.habit.frequency;

import com.habittracker.habit.FrequencyType;
import com.habittracker.habit.Habit;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public class SpecificDaysFrequencyStrategy implements HabitFrequencyStrategy {

  @Override
  public FrequencyType type() {
    return FrequencyType.SPECIFIC_DAYS;
  }

  @Override
  public boolean isScheduledOn(Habit habit, LocalDate date) {
    return habit.weekdayNumbers().contains(date.getDayOfWeek().getValue());
  }

  @Override
  public List<LocalDate> scheduledDates(
      Habit habit, LocalDate startInclusive, LocalDate endInclusive) {
    return everyDate(startInclusive, endInclusive).stream()
        .filter(date -> isScheduledOn(habit, date))
        .toList();
  }

  @Override
  public int expectedCompletions(
      Habit habit, LocalDate startInclusive, LocalDate endInclusive, LocalDate referenceDate) {
    LocalDate start = effectiveStart(habit, startInclusive);
    LocalDate end = capAtReference(endInclusive, referenceDate);
    if (start.isAfter(end)) {
      return 0;
    }
    return scheduledDates(habit, start, end).size();
  }

  @Override
  public StreakResult calculateStreak(
      Habit habit, Set<LocalDate> completedDates, LocalDate referenceDate) {
    LocalDate cursor = previousScheduled(habit, referenceDate);
    if (cursor != null && cursor.equals(referenceDate) && !completedDates.contains(referenceDate)) {
      // Today is scheduled but still open - give the user the rest of the day.
      cursor = previousScheduled(habit, referenceDate.minusDays(1));
    }

    int current = 0;
    while (cursor != null && completedDates.contains(cursor)) {
      current++;
      cursor = previousScheduled(habit, cursor.minusDays(1));
    }

    int longest = 0;
    Map<LocalDate, Integer> runs = new HashMap<>();
    for (LocalDate date : new TreeSet<>(completedDates)) {
      if (!isScheduledOn(habit, date)) {
        continue;
      }
      LocalDate previous = previousScheduled(habit, date.minusDays(1));
      int run = 1 + (previous == null ? 0 : runs.getOrDefault(previous, 0));
      runs.put(date, run);
      longest = Math.max(longest, run);
    }
    return StreakResult.days(current, longest);
  }

  private LocalDate previousScheduled(Habit habit, LocalDate date) {
    LocalDate cursor = date;
    for (int i = 0; i < 7; i++) {
      if (isScheduledOn(habit, cursor)) {
        return cursor;
      }
      cursor = cursor.minusDays(1);
    }
    return null;
  }
}
