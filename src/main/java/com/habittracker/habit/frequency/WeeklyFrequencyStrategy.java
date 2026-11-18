package com.habittracker.habit.frequency;

import com.habittracker.habit.FrequencyType;
import com.habittracker.habit.Habit;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WeeklyFrequencyStrategy implements HabitFrequencyStrategy {

  @Override
  public FrequencyType type() {
    return FrequencyType.WEEKLY;
  }

  @Override
  public boolean isScheduledOn(Habit habit, LocalDate date) {
    // Flexible weekly habits can be completed on any day of the week.
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
    long days = ChronoUnit.DAYS.between(start, end) + 1;
    long weeks = (days + 6) / 7;
    int target = habit.getTimesPerWeek() == null ? 1 : habit.getTimesPerWeek();
    return (int) Math.min(days, weeks * target);
  }

  @Override
  public StreakResult calculateStreak(
      Habit habit, Set<LocalDate> completedDates, LocalDate referenceDate) {
    int target = habit.getTimesPerWeek() == null ? 1 : habit.getTimesPerWeek();
    Map<LocalDate, Integer> counts = weeklyCounts(completedDates, referenceDate);

    LocalDate currentWeek = weekStart(referenceDate);
    int current = 0;
    if (counts.getOrDefault(currentWeek, 0) >= target) {
      current++;
    }
    LocalDate cursor = currentWeek.minusWeeks(1);
    while (counts.getOrDefault(cursor, 0) >= target) {
      current++;
      cursor = cursor.minusWeeks(1);
    }

    int longest = 0;
    int run = 0;
    LocalDate previous = null;
    List<LocalDate> qualifyingWeeks =
        counts.entrySet().stream()
            .filter(entry -> entry.getValue() >= target)
            .map(Map.Entry::getKey)
            .sorted()
            .toList();
    for (LocalDate week : qualifyingWeeks) {
      if (previous != null && previous.plusWeeks(1).equals(week)) {
        run++;
      } else {
        run = 1;
      }
      longest = Math.max(longest, run);
      previous = week;
    }
    return StreakResult.weeks(current, longest);
  }

  private Map<LocalDate, Integer> weeklyCounts(
      Set<LocalDate> completedDates, LocalDate referenceDate) {
    Map<LocalDate, Integer> counts = new HashMap<>();
    for (LocalDate date : completedDates) {
      if (date.isAfter(referenceDate)) {
        continue;
      }
      counts.merge(weekStart(date), 1, Integer::sum);
    }
    return counts;
  }

  private LocalDate weekStart(LocalDate date) {
    return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
  }
}
