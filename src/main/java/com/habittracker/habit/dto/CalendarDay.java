package com.habittracker.habit.dto;

import java.time.LocalDate;

public record CalendarDay(LocalDate date, boolean scheduled, boolean completed, String note) {}
