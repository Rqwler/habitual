package com.habittracker.habit.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReorderRequest(@NotEmpty List<@NotNull Long> habitIds) {}
