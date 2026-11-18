package com.habittracker.habit.frequency;

public record StreakResult(int current, int longest, String unit) {

  public static StreakResult days(int current, int longest) {
    return new StreakResult(current, longest, "days");
  }

  public static StreakResult weeks(int current, int longest) {
    return new StreakResult(current, longest, "weeks");
  }
}
