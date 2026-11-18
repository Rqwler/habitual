package com.habittracker.habit.frequency;

import com.habittracker.habit.FrequencyType;
import com.habittracker.habit.Habit;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

@Component
public class DailyFrequencyStrategy implements HabitFrequencyStrategy {

  @Override
  public FrequencyType type() {
    return FrequencyType.DAILY;
  }

  @Override
  public boolean isScheduledOn(Habit habit, LocalDate date) {
    return true;
  }

  @Override
  public List<LocalDate> scheduledDates(
      Habit habit, LocalDate startInclusive, LocalDate endInclusive) {
    return everyDate(startInclusive, endInclusive);
  }

  @Override
  public int expectedCompletions(
      Habit habit, LocalDate startInclusive, LocalDate endInclusive, LocalDate referenceDate) {
    LocalDate start = effectiveStart(habit, startInclusive);
    LocalDate end = capAtReference(endInclusive, referenceDate);
    if (start.isAfter(end)) {
      return 0;
    }
    return (int) (ChronoUnit.DAYS.between(start, end) + 1);
  }

  @Override
  public StreakResult calculateStreak(
      Habit habit, Set<LocalDate> completedDates, LocalDate referenceDate) {
    int current = 0;
    LocalDate cursor = referenceDate;
    if (!completedDates.contains(cursor)) {
      // Today is not over yet, so an unchecked today does not break the streak.
      cursor = cursor.minusDays(1);
    }
    while (completedDates.contains(cursor)) {
      current++;
      cursor = cursor.minusDays(1);
    }

    int longest = 0;
    int run = 0;
    LocalDate previous = null;
    for (LocalDate date : new TreeSet<>(completedDates)) {
      if (previous != null && date.equals(previous.plusDays(1))) {
        run++;
      } else {
        run = 1;
      }
      longest = Math.max(longest, run);
      previous = date;
    }
    return StreakResult.days(current, longest);
  }
}
