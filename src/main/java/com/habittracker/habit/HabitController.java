package com.habittracker.habit;

import com.habittracker.habit.dto.CheckInRequest;
import com.habittracker.habit.dto.CheckInResponse;
import com.habittracker.habit.dto.HabitCalendarResponse;
import com.habittracker.habit.dto.HabitRequest;
import com.habittracker.habit.dto.HabitResponse;
import com.habittracker.habit.dto.ReorderRequest;
import com.habittracker.security.UserPrincipal;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/habits")
@RequiredArgsConstructor
@Tag(name = "Habits")
public class HabitController {

  private final HabitService habitService;

  @GetMapping
  public List<HabitResponse> list(
      @AuthenticationPrincipal UserPrincipal principal,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return habitService.list(principal.id(), date);
  }

  @GetMapping("/{habitId}")
  public HabitResponse get(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long habitId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    return habitService.get(principal.id(), habitId, date);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public HabitResponse create(
      @AuthenticationPrincipal UserPrincipal principal, @Valid @RequestBody HabitRequest request) {
    return habitService.create(principal.id(), request);
  }

  @PutMapping("/{habitId}")
  public HabitResponse update(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long habitId,
      @Valid @RequestBody HabitRequest request) {
    return habitService.update(principal.id(), habitId, request);
  }

  @DeleteMapping("/{habitId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@AuthenticationPrincipal UserPrincipal principal, @PathVariable Long habitId) {
    habitService.delete(principal.id(), habitId);
  }

  @PutMapping("/order")
  public List<HabitResponse> reorder(
      @AuthenticationPrincipal UserPrincipal principal,
      @Valid @RequestBody ReorderRequest request) {
    return habitService.reorder(principal.id(), request.habitIds());
  }

  @PostMapping("/{habitId}/check-ins")
  public CheckInResponse checkIn(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long habitId,
      @Valid @RequestBody CheckInRequest request) {
    return habitService.checkIn(principal.id(), habitId, request);
  }

  @DeleteMapping("/{habitId}/check-ins")
  public CheckInResponse undoCheckIn(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long habitId,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return habitService.undoCheckIn(principal.id(), habitId, date);
  }

  @GetMapping("/{habitId}/calendar")
  public HabitCalendarResponse calendar(
      @AuthenticationPrincipal UserPrincipal principal,
      @PathVariable Long habitId,
      @RequestParam(required = false) Integer year,
      @RequestParam(required = false) Integer month) {
    return habitService.calendar(principal.id(), habitId, year, month);
  }

  @PostMapping("/{habitId}/archive")
  public HabitResponse archive(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long habitId) {
    return habitService.archive(principal.id(), habitId, true);
  }

  @PostMapping("/{habitId}/unarchive")
  public HabitResponse unarchive(
      @AuthenticationPrincipal UserPrincipal principal, @PathVariable Long habitId) {
    return habitService.archive(principal.id(), habitId, false);
  }
}
