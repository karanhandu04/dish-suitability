package se.nexus.dishsuitability.domain;

import java.time.LocalTime;

/**
 * Strategy for scoring one dish against a time of day.
 *
 * <p>One implementation per dish. The service picks the right one at runtime, so adding a dish
 * never means editing an if/else or switch in existing code (open/closed principle).
 */
public interface DishScorer {

  /** The dish this scorer is responsible for. */
  Dish dish();

  /** How suitable {@link #dish()} is at the given time. */
  Score score(LocalTime time);
}
