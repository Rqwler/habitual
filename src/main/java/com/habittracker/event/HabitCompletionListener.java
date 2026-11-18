package com.habittracker.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class HabitCompletionListener {

  private static final Logger log = LoggerFactory.getLogger(HabitCompletionListener.class);

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onHabitCompleted(HabitCompletedEvent event) {
    log.info("Habit {} checked in by user {} on {}", event.habitId(), event.userId(), event.date());
  }
}
