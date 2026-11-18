package com.habittracker.habit.frequency;

import static org.assertj.core.api.Assertions.assertThat;

import com.habittracker.habit.FrequencyType;
import com.habittracker.habit.Habit;
import com.habittracker.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WeeklyFrequencyStrategyTest {

  private final WeeklyFrequencyStrategy strategy = new WeeklyFrequencyStrategy();
  private final LocalDate reference = LocalDate.of(2026, 11, 14);

  @Test
  void countsConsecutiveWeeksThatMeetTheTarget() {
    Set<LocalDate> completed =
        Set.of(
            // Week of Nov 9: target met.
            LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 10),
            LocalDate.of(2026, 11, 11),
            // Week of Nov 2: target met.
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 3),
            LocalDate.of(2026, 11, 5),
            // Week of Oct 26: target not met.
            LocalDate.of(2026, 10, 27),
            LocalDate.of(2026, 10, 28));

    StreakResult streak = strategy.calculateStreak(habit(), completed, reference);

    assertThat(streak.current()).isEqualTo(2);
    assertThat(streak.longest()).isEqualTo(2);
    assertThat(streak.unit()).isEqualTo("weeks");
  }

  @Test
  void currentWeekIsStillOpenWhenTargetIsNotMetYet() {
    Set<LocalDate> completed =
        Set.of(
            LocalDate.of(2026, 11, 9),
            LocalDate.of(2026, 11, 2),
            LocalDate.of(2026, 11, 3),
            LocalDate.of(2026, 11, 5));

    StreakResult streak = strategy.calculateStreak(habit(), completed, reference);

    assertThat(streak.current()).isEqualTo(1);
  }

  @Test
  void expectedCompletionsCapsTargetAtAvailableDays() {
    Habit habit = habit();

    assertThat(strategy.expectedCompletions(habit, reference, reference, reference)).isEqualTo(1);
    assertThat(
            strategy.expectedCompletions(
                habit, LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15), reference))
        .isEqualTo(3);
    assertThat(
            strategy.expectedCompletions(
                habit, LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 30), reference))
        .isEqualTo(6);
  }

  private Habit habit() {
    Habit habit =
        new Habit(
            new User("test@example.com", "Test", "hash"),
            "Yoga",
            null,
            "Wellbeing",
            FrequencyType.WEEKLY);
    habit.setTimesPerWeek(3);
    habit.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));
    return habit;
  }
}
