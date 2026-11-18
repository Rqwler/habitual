package com.habittracker.habit;

import com.habittracker.common.BadRequestException;
import com.habittracker.common.ResourceNotFoundException;
import com.habittracker.event.HabitCompletedEvent;
import com.habittracker.habit.dto.CalendarDay;
import com.habittracker.habit.dto.CheckInRequest;
import com.habittracker.habit.dto.CheckInResponse;
import com.habittracker.habit.dto.HabitCalendarResponse;
import com.habittracker.habit.dto.HabitRequest;
import com.habittracker.habit.dto.HabitResponse;
import com.habittracker.habit.frequency.FrequencyStrategyRegistry;
import com.habittracker.habit.frequency.HabitFrequencyStrategy;
import com.habittracker.log.HabitLog;
import com.habittracker.log.HabitLogRepository;
import com.habittracker.user.User;
import com.habittracker.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HabitService {

  private final HabitRepository habitRepository;
  private final HabitLogRepository habitLogRepository;
  private final UserRepository userRepository;
  private final HabitMetricsService metricsService;
  private final FrequencyStrategyRegistry frequencyStrategyRegistry;
  private final ApplicationEventPublisher eventPublisher;
  private final Clock clock;

  @Transactional(readOnly = true)
  public List<HabitResponse> list(Long userId, LocalDate referenceDate) {
    LocalDate date = referenceDate == null ? LocalDate.now(clock) : referenceDate;
    List<Habit> habits =
        habitRepository.findByUserIdAndArchivedFalseOrderByPriorityDescCreatedAtAsc(userId);
    Map<Long, Set<LocalDate>> logsByHabit = logsByHabit(userId);
    return habits.stream()
        .map(habit -> toResponse(habit, logsByHabit.getOrDefault(habit.getId(), Set.of()), date))
        .toList();
  }

  @Transactional(readOnly = true)
  public HabitResponse get(Long userId, Long habitId, LocalDate referenceDate) {
    Habit habit = findOwned(userId, habitId);
    LocalDate date = referenceDate == null ? LocalDate.now(clock) : referenceDate;
    return toResponse(habit, completedDates(habitId), date);
  }

  @Transactional
  public HabitResponse create(Long userId, HabitRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    Habit habit =
        new Habit(
            user,
            request.name().trim(),
            trimToNull(request.description()),
            trimToNull(request.category()),
            request.frequencyType());
    applyFrequency(habit, request);
    habit.setPriority(habitRepository.findMinPriority(userId) - 1);
    habit.setCreatedAt(Instant.now(clock));
    habitRepository.save(habit);
    return toResponse(habit, Set.of(), LocalDate.now(clock));
  }

  @Transactional
  public HabitResponse update(Long userId, Long habitId, HabitRequest request) {
    Habit habit = findOwned(userId, habitId);
    habit.setName(request.name().trim());
    habit.setDescription(trimToNull(request.description()));
    habit.setCategory(trimToNull(request.category()));
    applyFrequency(habit, request);
    habitRepository.save(habit);
    return toResponse(habit, completedDates(habitId), LocalDate.now(clock));
  }

  @Transactional
  public void delete(Long userId, Long habitId) {
    Habit habit = findOwned(userId, habitId);
    habitLogRepository.deleteByHabitId(habit.getId());
    habitRepository.delete(habit);
  }

  @Transactional
  public List<HabitResponse> reorder(Long userId, List<Long> habitIds) {
    List<Habit> habits =
        habitRepository.findByUserIdAndArchivedFalseOrderByPriorityDescCreatedAtAsc(userId);
    Set<Long> existingIds =
        habits.stream().map(Habit::getId).collect(Collectors.toCollection(HashSet::new));
    if (habitIds.size() != habits.size() || !existingIds.equals(new HashSet<>(habitIds))) {
      throw new BadRequestException("The order must contain every active habit exactly once");
    }

    Map<Long, Habit> byId =
        habits.stream().collect(Collectors.toMap(Habit::getId, Function.identity()));
    int priority = habits.size() - 1;
    for (Long habitId : habitIds) {
      byId.get(habitId).setPriority(priority--);
    }
    habitRepository.saveAll(habits);
    return list(userId, LocalDate.now(clock));
  }

  @Transactional
  public HabitResponse archive(Long userId, Long habitId, boolean archived) {
    Habit habit = findOwned(userId, habitId);
    if (!archived && habit.isArchived()) {
      habit.setPriority(habitRepository.findMinPriority(userId) - 1);
    }
    habit.setArchived(archived);
    habitRepository.save(habit);
    return toResponse(habit, completedDates(habitId), LocalDate.now(clock));
  }

  @Transactional
  public CheckInResponse checkIn(Long userId, Long habitId, CheckInRequest request) {
    Habit habit = findOwned(userId, habitId);
    if (habit.isArchived()) {
      throw new BadRequestException("Archived habits cannot be checked in");
    }

    LocalDate today = LocalDate.now(clock);
    LocalDate date = request.date();
    if (date.isAfter(today)) {
      throw new BadRequestException("Future check-ins are not allowed");
    }

    HabitFrequencyStrategy strategy = frequencyStrategyRegistry.forType(habit.getFrequencyType());
    if (!strategy.isScheduledOn(habit, date)) {
      throw new BadRequestException("This habit is not scheduled on " + date);
    }

    HabitLog existing = habitLogRepository.findByHabitIdAndLogDate(habitId, date).orElse(null);
    boolean created = existing == null;
    if (created) {
      habitLogRepository.save(new HabitLog(habit, date, trimToNull(request.note())));
      eventPublisher.publishEvent(new HabitCompletedEvent(habitId, userId, date));
    } else if (request.note() != null) {
      existing.setNote(trimToNull(request.note()));
    }

    return toCheckInResponse(
        habit, date, true, created ? "Checked in" : "Already checked in for this day");
  }

  @Transactional
  public CheckInResponse undoCheckIn(Long userId, Long habitId, LocalDate date) {
    Habit habit = findOwned(userId, habitId);
    habitLogRepository.findByHabitIdAndLogDate(habitId, date).ifPresent(habitLogRepository::delete);
    return toCheckInResponse(habit, date, false, "Check-in removed");
  }

  @Transactional(readOnly = true)
  public HabitCalendarResponse calendar(Long userId, Long habitId, Integer year, Integer month) {
    Habit habit = findOwned(userId, habitId);
    LocalDate today = LocalDate.now(clock);
    int safeYear = year == null ? today.getYear() : year;
    int safeMonth = month == null ? today.getMonthValue() : month;
    if (safeYear < 2000 || safeYear > 2100 || safeMonth < 1 || safeMonth > 12) {
      throw new BadRequestException("Year must be 2000-2100 and month must be 1-12");
    }

    YearMonth yearMonth = YearMonth.of(safeYear, safeMonth);
    LocalDate start = yearMonth.atDay(1);
    LocalDate end = yearMonth.atEndOfMonth();
    HabitFrequencyStrategy strategy = frequencyStrategyRegistry.forType(habit.getFrequencyType());
    Map<LocalDate, String> notes =
        habitLogRepository
            .findByHabitIdAndLogDateBetweenOrderByLogDateAsc(habitId, start, end)
            .stream()
            .collect(
                Collectors.toMap(
                    HabitLog::getLogDate, log -> log.getNote() == null ? "" : log.getNote()));

    List<CalendarDay> days =
        strategy.scheduledDates(habit, start, end).stream()
            .map(date -> new CalendarDay(date, true, notes.containsKey(date), notes.get(date)))
            .toList();

    int targetCount = strategy.expectedCompletions(habit, start, end, end);
    return new HabitCalendarResponse(
        habit.getId(),
        habit.getName(),
        habit.getFrequencyType(),
        safeYear,
        safeMonth,
        days,
        notes.size(),
        targetCount);
  }

  private void applyFrequency(Habit habit, HabitRequest request) {
    habit.setFrequencyType(request.frequencyType());
    switch (request.frequencyType()) {
      case DAILY -> {
        habit.setTimesPerWeek(null);
        habit.setWeekdayNumbers(List.of());
      }
      case WEEKLY -> {
        if (request.timesPerWeek() == null) {
          throw new BadRequestException("timesPerWeek is required for weekly habits");
        }
        habit.setWeekdayNumbers(List.of());
        habit.setTimesPerWeek(request.timesPerWeek());
      }
      case SPECIFIC_DAYS -> {
        if (request.weekdays() == null || request.weekdays().isEmpty()) {
          throw new BadRequestException("Pick at least one weekday");
        }
        habit.setTimesPerWeek(null);
        habit.setWeekdayNumbers(request.weekdays());
      }
    }
  }

  private CheckInResponse toCheckInResponse(
      Habit habit, LocalDate date, boolean completed, String message) {
    HabitMetrics metrics =
        metricsService.calculate(habit, completedDates(habit.getId()), LocalDate.now(clock));
    return new CheckInResponse(
        habit.getId(),
        date,
        completed,
        metrics.currentStreak(),
        metrics.longestStreak(),
        metrics.streakUnit(),
        metrics.weekCompleted(),
        metrics.weekTarget(),
        message);
  }

  private HabitResponse toResponse(
      Habit habit, Set<LocalDate> completedDates, LocalDate referenceDate) {
    HabitMetrics metrics = metricsService.calculate(habit, completedDates, referenceDate);
    return new HabitResponse(
        habit.getId(),
        habit.getName(),
        habit.getDescription(),
        habit.getCategory(),
        habit.getFrequencyType(),
        habit.getTimesPerWeek(),
        habit.weekdayNumbers(),
        habit.getPriority(),
        habit.isArchived(),
        habit.getCreatedAt(),
        metrics.currentStreak(),
        metrics.longestStreak(),
        metrics.streakUnit(),
        metrics.scheduledToday(),
        metrics.completedToday(),
        metrics.completionRate30d(),
        metrics.weekCompleted(),
        metrics.weekTarget());
  }

  private Habit findOwned(Long userId, Long habitId) {
    return habitRepository
        .findByIdAndUserId(habitId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Habit not found"));
  }

  private Set<LocalDate> completedDates(Long habitId) {
    return habitLogRepository.findByHabitIdOrderByLogDateAsc(habitId).stream()
        .map(HabitLog::getLogDate)
        .collect(Collectors.toSet());
  }

  private Map<Long, Set<LocalDate>> logsByHabit(Long userId) {
    return habitLogRepository.findAllByUserId(userId).stream()
        .collect(
            Collectors.groupingBy(
                log -> log.getHabit().getId(),
                Collectors.mapping(HabitLog::getLogDate, Collectors.toSet())));
  }

  private String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
