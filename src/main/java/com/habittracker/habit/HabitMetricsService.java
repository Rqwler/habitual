package com.habittracker.habit;

import com.habittracker.habit.frequency.FrequencyStrategyRegistry;
import com.habittracker.habit.frequency.HabitFrequencyStrategy;
import com.habittracker.habit.frequency.StreakResult;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class HabitMetricsService {

  private final FrequencyStrategyRegistry frequencyStrategyRegistry;

  public HabitMetrics calculate(
      Habit habit, Set<LocalDate> completedDates, LocalDate referenceDate) {
    HabitFrequencyStrategy strategy = frequencyStrategyRegistry.forType(habit.getFrequencyType());
    StreakResult streak = strategy.calculateStreak(habit, completedDates, referenceDate);

    LocalDate thirtyDaysAgo = referenceDate.minusDays(29);
    int expected30 =
        strategy.expectedCompletions(habit, thirtyDaysAgo, referenceDate, referenceDate);
    int completed30 =
        (int)
            completedDates.stream()
                .filter(date -> !date.isBefore(thirtyDaysAgo))
                .filter(date -> !date.isAfter(referenceDate))
                .count();
    int completionRate =
        expected30 == 0 ? 0 : Math.min(100, (int) Math.round(completed30 * 100.0 / expected30));

    LocalDate weekStart = referenceDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    int weekCompleted =
        (int)
            completedDates.stream()
                .filter(date -> !date.isBefore(weekStart))
                .filter(date -> !date.isAfter(referenceDate))
                .count();
    int weekTarget = strategy.expectedCompletions(habit, weekStart, referenceDate, referenceDate);

    return new HabitMetrics(
        streak.current(),
        streak.longest(),
        streak.unit(),
        completionRate,
        weekCompleted,
        weekTarget,
        strategy.isScheduledOn(habit, referenceDate),
        completedDates.contains(referenceDate));
  }
}
