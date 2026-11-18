package com.habittracker.habit.frequency;

import com.habittracker.habit.FrequencyType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FrequencyStrategyRegistry {

  private final Map<FrequencyType, HabitFrequencyStrategy> strategies =
      new EnumMap<>(FrequencyType.class);

  public FrequencyStrategyRegistry(List<HabitFrequencyStrategy> registeredStrategies) {
    for (HabitFrequencyStrategy strategy : registeredStrategies) {
      strategies.put(strategy.type(), strategy);
    }
  }

  public HabitFrequencyStrategy forType(FrequencyType frequencyType) {
    HabitFrequencyStrategy strategy = strategies.get(frequencyType);
    if (strategy == null) {
      throw new IllegalArgumentException("No frequency strategy for " + frequencyType);
    }
    return strategy;
  }
}
