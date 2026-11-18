package com.habittracker.habit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HabitRepository extends JpaRepository<Habit, Long> {

  List<Habit> findByUserIdAndArchivedFalseOrderByPriorityDescCreatedAtAsc(Long userId);

  List<Habit> findByUserIdOrderByPriorityDescCreatedAtAsc(Long userId);

  Optional<Habit> findByIdAndUserId(Long id, Long userId);

  long countByUserIdAndArchivedFalse(Long userId);

  @Query("select coalesce(min(h.priority), 0) from Habit h where h.user.id = :userId")
  int findMinPriority(@Param("userId") Long userId);
}
