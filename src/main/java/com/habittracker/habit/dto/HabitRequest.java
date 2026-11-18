package com.habittracker.habit.dto;

import com.habittracker.habit.FrequencyType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record HabitRequest(
    @NotBlank @Size(max = 120) String name,
    @Size(max = 1000) String description,
    @Size(max = 60) String category,
    @NotNull FrequencyType frequencyType,
    @Min(1) @Max(7) Integer timesPerWeek,
    List<@Min(1) @Max(7) Integer> weekdays) {}
