package com.habittracker.habit.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record CheckInRequest(@NotNull LocalDate date, @Size(max = 500) String note) {}
