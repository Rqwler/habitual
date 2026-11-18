package com.habittracker.habit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.habittracker.habit.frequency.FrequencyStrategyRegistry;
import com.habittracker.habit.frequency.HabitFrequencyStrategy;
import com.habittracker.habit.frequency.StreakResult;
import com.habittracker.user.User;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HabitMetricsServiceTest {

  private static final LocalDate REFERENCE = LocalDate.of(2026, 11, 14);
  private static final LocalDate WEEK_START =
      REFERENCE.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

  @Mock private FrequencyStrategyRegistry registry;
  @Mock private HabitFrequencyStrategy strategy;
  @InjectMocks private HabitMetricsService service;

  @Test
  void calculatesDailyMetricsFromStrategy() {
    Habit habit = habit(FrequencyType.DAILY);
    Set<LocalDate> completed =
        Set.of(
            REFERENCE,
            REFERENCE.minusDays(1),
            REFERENCE.minusDays(2),
            REFERENCE.minusDays(3),
            REFERENCE.minusDays(4),
            REFERENCE.minusDays(5));

    when(registry.forType(FrequencyType.DAILY)).thenReturn(strategy);
    when(strategy.calculateStreak(habit, completed, REFERENCE))
        .thenReturn(StreakResult.days(6, 10));
    when(strategy.expectedCompletions(habit, REFERENCE.minusDays(29), REFERENCE, REFERENCE))
        .thenReturn(30);
    when(strategy.expectedCompletions(habit, WEEK_START, REFERENCE, REFERENCE)).thenReturn(6);
    when(strategy.isScheduledOn(habit, REFERENCE)).thenReturn(true);

    HabitMetrics metrics = service.calculate(habit, completed, REFERENCE);

    assertThat(metrics.currentStreak()).isEqualTo(6);
    assertThat(metrics.longestStreak()).isEqualTo(10);
    assertThat(metrics.streakUnit()).isEqualTo("days");
    assertThat(metrics.completionRate30d()).isEqualTo(20);
    assertThat(metrics.weekCompleted()).isEqualTo(6);
    assertThat(metrics.weekTarget()).isEqualTo(6);
    assertThat(metrics.scheduledToday()).isTrue();
    assertThat(metrics.completedToday()).isTrue();
    verify(registry).forType(FrequencyType.DAILY);
  }

  @Test
  void capsCompletionRateAtHundredPercentAndUsesWeekStreaks() {
    Habit habit = habit(FrequencyType.WEEKLY);
    Set<LocalDate> completed =
        Set.of(REFERENCE, REFERENCE.minusDays(1), REFERENCE.minusDays(2), REFERENCE.minusDays(3));

    when(registry.forType(FrequencyType.WEEKLY)).thenReturn(strategy);
    when(strategy.calculateStreak(habit, completed, REFERENCE))
        .thenReturn(StreakResult.weeks(2, 5));
    when(strategy.expectedCompletions(habit, REFERENCE.minusDays(29), REFERENCE, REFERENCE))
        .thenReturn(3);
    when(strategy.expectedCompletions(habit, WEEK_START, REFERENCE, REFERENCE)).thenReturn(3);
    when(strategy.isScheduledOn(habit, REFERENCE)).thenReturn(true);

    HabitMetrics metrics = service.calculate(habit, completed, REFERENCE);

    assertThat(metrics.streakUnit()).isEqualTo("weeks");
    assertThat(metrics.completionRate30d()).isEqualTo(100);
    assertThat(metrics.weekCompleted()).isEqualTo(4);
    assertThat(metrics.weekTarget()).isEqualTo(3);
    assertThat(metrics.completedToday()).isTrue();
  }

  @Test
  void returnsZeroRateWhenNothingIsExpected() {
    Habit habit = habit(FrequencyType.DAILY);
    Set<LocalDate> completed = Set.of();

    when(registry.forType(FrequencyType.DAILY)).thenReturn(strategy);
    when(strategy.calculateStreak(habit, completed, REFERENCE)).thenReturn(StreakResult.days(0, 0));
    when(strategy.expectedCompletions(habit, REFERENCE.minusDays(29), REFERENCE, REFERENCE))
        .thenReturn(0);
    when(strategy.expectedCompletions(habit, WEEK_START, REFERENCE, REFERENCE)).thenReturn(0);
    when(strategy.isScheduledOn(habit, REFERENCE)).thenReturn(false);

    HabitMetrics metrics = service.calculate(habit, completed, REFERENCE);

    assertThat(metrics.completionRate30d()).isZero();
    assertThat(metrics.weekTarget()).isZero();
    assertThat(metrics.scheduledToday()).isFalse();
    assertThat(metrics.completedToday()).isFalse();
  }

  private Habit habit(FrequencyType type) {
    return new Habit(
        new User("metrics-test@example.com", "Metrics Test", "hash"), "Read", null, null, type);
  }
}
