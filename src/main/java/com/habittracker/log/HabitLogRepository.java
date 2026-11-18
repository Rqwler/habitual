package com.habittracker.log;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface HabitLogRepository extends JpaRepository<HabitLog, Long> {

  Optional<HabitLog> findByHabitIdAndLogDate(Long habitId, LocalDate logDate);

  List<HabitLog> findByHabitIdOrderByLogDateAsc(Long habitId);

  List<HabitLog> findByHabitIdAndLogDateBetweenOrderByLogDateAsc(
      Long habitId, LocalDate start, LocalDate end);

  void deleteByHabitId(Long habitId);

  @Query(
      "select l from HabitLog l join fetch l.habit h "
          + "where h.user.id = :userId order by l.logDate asc")
  List<HabitLog> findAllByUserId(@Param("userId") Long userId);
}
