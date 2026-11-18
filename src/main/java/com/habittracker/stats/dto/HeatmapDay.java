package com.habittracker.stats.dto;

import java.time.LocalDate;

public record HeatmapDay(LocalDate date, int completed, int scheduled) {}
