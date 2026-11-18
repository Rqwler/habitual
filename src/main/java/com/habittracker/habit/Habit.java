package com.habittracker.habit;

import com.habittracker.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "habits")
@Getter
@Setter
@NoArgsConstructor
public class Habit {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private User user;

  @Column(nullable = false, length = 120)
  private String name;

  @Column(length = 1000)
  private String description;

  @Column(length = 60)
  private String category;

  @Enumerated(EnumType.STRING)
  @Column(name = "frequency_type", nullable = false, length = 20)
  private FrequencyType frequencyType;

  @Column(name = "times_per_week")
  private Integer timesPerWeek;

  /** ISO weekday numbers (Monday = 1 ... Sunday = 7), stored as a CSV string. */
  @Column(length = 20)
  private String weekdays;

  @Column(nullable = false)
  private int priority;

  @Column(nullable = false)
  private boolean archived;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Habit(
      User user, String name, String description, String category, FrequencyType frequencyType) {
    this.user = user;
    this.name = name;
    this.description = description;
    this.category = category;
    this.frequencyType = frequencyType;
  }

  public List<Integer> weekdayNumbers() {
    if (weekdays == null || weekdays.isBlank()) {
      return List.of();
    }
    return Arrays.stream(weekdays.split(","))
        .map(String::trim)
        .filter(value -> !value.isEmpty())
        .map(Integer::valueOf)
        .filter(day -> day >= 1 && day <= 7)
        .distinct()
        .sorted()
        .toList();
  }

  public void setWeekdayNumbers(List<Integer> days) {
    if (days == null || days.isEmpty()) {
      this.weekdays = null;
      return;
    }
    this.weekdays =
        days.stream()
            .filter(Objects::nonNull)
            .filter(day -> day >= 1 && day <= 7)
            .distinct()
            .sorted()
            .map(String::valueOf)
            .reduce((left, right) -> left + "," + right)
            .orElse(null);
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
