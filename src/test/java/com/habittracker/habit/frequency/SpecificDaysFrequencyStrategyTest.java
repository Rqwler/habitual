package com.habittracker.habit.frequency;

import static org.assertj.core.api.Assertions.assertThat;

import com.habittracker.habit.FrequencyType;
import com.habittracker.habit.Habit;
import com.habittracker.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SpecificDaysFrequencyStrategyTest {

  private final SpecificDaysFrequencyStrategy strategy = new SpecificDaysFrequencyStrategy();
  private final LocalDate reference = LocalDate.of(2026, 11, 14);

  @Test
  void onlyCountsSelectedWeekdays() {
    Habit habit = habit();

    List<LocalDate> scheduled =
        strategy.scheduledDates(habit, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15));

    assertThat(scheduled)
        .containsExactly(
            LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 13));
  }

  @Test
  void followsTheScheduledSequenceForStreaks() {
    Habit habit = habit();
    Set<LocalDate> completed =
        Set.of(
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 4),
            LocalDate.of(2026, 11, 6),
            LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 11),
            LocalDate.of(2026, 11, 13));

    StreakResult streak = strategy.calculateStreak(habit, completed, reference);

    assertThat(streak.current()).isEqualTo(6);
    assertThat(streak.longest()).isEqualTo(6);
  }

  @Test
  void breaksWhenAPastScheduledDayIsMissed() {
    Habit habit = habit();
    Set<LocalDate> completed =
        Set.of(LocalDate.of(2026, 11, 10), LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 12));

    // Only Nov 11 (Wednesday) is scheduled for this Mon/Wed/Fri habit.
    StreakResult streak = strategy.calculateStreak(habit, completed, reference);

    assertThat(streak.current()).isZero();
    assertThat(streak.longest()).isEqualTo(1);
  }

  @Test
  void expectedCompletionsCountScheduledDaysOnly() {
    int expected =
        strategy.expectedCompletions(
            habit(), LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15), reference);

    assertThat(expected).isEqualTo(3);
  }

  private Habit habit() {
    Habit habit =
        new Habit(
            new User("test@example.com", "Test", "hash"),
            "Strength training",
            null,
            "Fitness",
            FrequencyType.SPECIFIC_DAYS);
    habit.setWeekdayNumbers(List.of(1, 3, 5));
    habit.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    return habit;
  }
}
