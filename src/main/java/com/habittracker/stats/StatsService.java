package com.habittracker.stats;

import com.habittracker.habit.Habit;
import com.habittracker.habit.HabitMetrics;
import com.habittracker.habit.HabitMetricsService;
import com.habittracker.habit.HabitRepository;
import com.habittracker.habit.frequency.FrequencyStrategyRegistry;
import com.habittracker.habit.frequency.HabitFrequencyStrategy;
import com.habittracker.log.HabitLog;
import com.habittracker.log.HabitLogRepository;
import com.habittracker.stats.dto.DashboardResponse;
import com.habittracker.stats.dto.HeatmapDay;
import com.habittracker.stats.dto.PeriodSummary;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StatsService {

  private final HabitRepository habitRepository;
  private final HabitLogRepository habitLogRepository;
  private final FrequencyStrategyRegistry frequencyStrategyRegistry;
  private final HabitMetricsService metricsService;
  private final Clock clock;

  @Transactional(readOnly = true)
  public DashboardResponse dashboard(Long userId, LocalDate requestedDate) {
    LocalDate today = requestedDate == null ? LocalDate.now(clock) : requestedDate;
    List<Habit> habits =
        habitRepository.findByUserIdAndArchivedFalseOrderByPriorityDescCreatedAtAsc(userId);
    List<HabitLog> logs = habitLogRepository.findAllByUserId(userId);
    Map<Long, Set<LocalDate>> logsByHabit =
        logs.stream()
            .collect(
                Collectors.groupingBy(
                    log -> log.getHabit().getId(),
                    Collectors.mapping(HabitLog::getLogDate, Collectors.toSet())));

    LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    YearMonth yearMonth = YearMonth.from(today);

    int bestCurrentStreak = 0;
    String bestStreakUnit = "days";
    int bestStreakInDays = 0;
    for (Habit habit : habits) {
      HabitMetrics metrics =
          metricsService.calculate(habit, logsByHabit.getOrDefault(habit.getId(), Set.of()), today);
      int equivalentDays =
          "weeks".equals(metrics.streakUnit())
              ? metrics.currentStreak() * 7
              : metrics.currentStreak();
      if (equivalentDays > bestStreakInDays) {
        bestStreakInDays = equivalentDays;
        bestCurrentStreak = metrics.currentStreak();
        bestStreakUnit = metrics.streakUnit();
      }
    }

    long totalCheckIns = logs.stream().filter(log -> !log.getLogDate().isAfter(today)).count();

    return new DashboardResponse(
        today,
        summarize(habits, logsByHabit, today, today, today),
        summarize(habits, logsByHabit, weekStart, weekStart.plusDays(6), today),
        summarize(habits, logsByHabit, yearMonth.atDay(1), yearMonth.atEndOfMonth(), today),
        habits.size(),
        bestCurrentStreak,
        bestStreakUnit,
        totalCheckIns);
  }

  @Transactional(readOnly = true)
  public List<HeatmapDay> heatmap(Long userId, int requestedDays) {
    int days = Math.max(1, Math.min(requestedDays, 730));
    LocalDate today = LocalDate.now(clock);
    LocalDate start = today.minusDays(days - 1L);
    List<Habit> habits =
        habitRepository.findByUserIdAndArchivedFalseOrderByPriorityDescCreatedAtAsc(userId);
    Map<Long, Set<LocalDate>> logsByHabit =
        habitLogRepository.findAllByUserId(userId).stream()
            .collect(
                Collectors.groupingBy(
                    log -> log.getHabit().getId(),
                    Collectors.mapping(HabitLog::getLogDate, Collectors.toSet())));

    List<HeatmapDay> result = new ArrayList<>();
    for (LocalDate date = start; !date.isAfter(today); date = date.plusDays(1)) {
      int scheduled = 0;
      int completed = 0;
      for (Habit habit : habits) {
        HabitFrequencyStrategy strategy =
            frequencyStrategyRegistry.forType(habit.getFrequencyType());
        if (strategy.isScheduledOn(habit, date)) {
          scheduled++;
          if (logsByHabit.getOrDefault(habit.getId(), Set.of()).contains(date)) {
            completed++;
          }
        }
      }
      result.add(new HeatmapDay(date, completed, scheduled));
    }
    return result;
  }

  private PeriodSummary summarize(
      List<Habit> habits,
      Map<Long, Set<LocalDate>> logsByHabit,
      LocalDate start,
      LocalDate end,
      LocalDate referenceDate) {
    int completed = 0;
    int expected = 0;
    for (Habit habit : habits) {
      HabitFrequencyStrategy strategy = frequencyStrategyRegistry.forType(habit.getFrequencyType());
      expected += strategy.expectedCompletions(habit, start, end, referenceDate);
      Set<LocalDate> dates = logsByHabit.getOrDefault(habit.getId(), Set.of());
      completed +=
          (int)
              dates.stream()
                  .filter(date -> !date.isBefore(start))
                  .filter(date -> !date.isAfter(end))
                  .filter(date -> !date.isAfter(referenceDate))
                  .count();
    }
    int percentage =
        expected == 0 ? 0 : Math.min(100, (int) Math.round(completed * 100.0 / expected));
    return new PeriodSummary(start, end, completed, expected, percentage);
  }
}
