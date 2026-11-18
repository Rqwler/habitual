package com.habittracker.habit.frequency;

import static org.assertj.core.api.Assertions.assertThat;

import com.habittracker.habit.FrequencyType;
import com.habittracker.habit.Habit;
import com.habittracker.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DailyFrequencyStrategyTest {

  private final DailyFrequencyStrategy strategy = new DailyFrequencyStrategy();
  private final LocalDate reference = LocalDate.of(2026, 11, 14);

  @Test
  void countsCurrentStreakAndAllowsTodayToStayOpen() {
    Set<LocalDate> completed =
        Set.of(
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 11, 11),
            LocalDate.of(2026, 11, 12),
            LocalDate.of(2026, 11, 13));

    StreakResult streak = strategy.calculateStreak(habit(), completed, reference);

    assertThat(streak.current()).isEqualTo(4);
    assertThat(streak.longest()).isEqualTo(4);
    assertThat(streak.unit()).isEqualTo("days");
  }

  @Test
  void findsLongestStreakAcrossGaps() {
    Set<LocalDate> completed =
        Set.of(
            LocalDate.of(2026, 11, 1),
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 3),
            LocalDate.of(2026, 11, 6),
            LocalDate.of(2026, 11, 7));

    StreakResult streak = strategy.calculateStreak(habit(), completed, reference);

    assertThat(streak.current()).isZero();
    assertThat(streak.longest()).isEqualTo(3);
  }

  @Test
  void countsTodayWhenItIsAlreadyCompleted() {
    Set<LocalDate> completed =
        Set.of(LocalDate.of(2026, 11, 12), LocalDate.of(2026, 11, 13), reference);

    assertThat(strategy.calculateStreak(habit(), completed, reference).current()).isEqualTo(3);
  }

  @Test
  void expectedCompletionsRespectHabitCreationDate() {
    Habit habit = habit();
    habit.setCreatedAt(Instant.parse("2026-11-10T09:00:00Z"));

    int expected =
        strategy.expectedCompletions(habit, LocalDate.of(2026, 11, 1), reference, reference);

    assertThat(expected).isEqualTo(5);
  }

  private Habit habit() {
    Habit habit =
        new Habit(
            new User("test@example.com", "Test", "hash"),
            "Morning walk",
            null,
            "Health",
            FrequencyType.DAILY);
    habit.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    return habit;
  }
}
