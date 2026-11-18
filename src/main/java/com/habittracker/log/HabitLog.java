package com.habittracker.log;

import com.habittracker.habit.Habit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
    name = "habit_logs",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_habit_logs_habit_date",
            columnNames = {"habit_id", "log_date"}))
@Getter
@Setter
@NoArgsConstructor
public class HabitLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "habit_id", nullable = false)
  private Habit habit;

  @Column(name = "log_date", nullable = false)
  private LocalDate logDate;

  @Column(length = 500)
  private String note;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public HabitLog(Habit habit, LocalDate logDate, String note) {
    this.habit = habit;
    this.logDate = logDate;
    this.note = note;
  }

  @PrePersist
  void onCreate() {
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }
}
