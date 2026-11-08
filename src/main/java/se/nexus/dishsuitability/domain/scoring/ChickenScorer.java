package se.nexus.dishsuitability.domain.scoring;

import java.util.List;
import org.springframework.stereotype.Component;
import se.nexus.dishsuitability.domain.Dish;
import se.nexus.dishsuitability.domain.Score;
import se.nexus.dishsuitability.domain.TimeWindow;
import se.nexus.dishsuitability.domain.TimeWindowDishScorer;

/** Chicken: a main course, best at dinner, good at lunch, unusual in the morning. */
@Component
public class ChickenScorer extends TimeWindowDishScorer {

  public ChickenScorer() {
    super(
        Dish.CHICKEN,
        List.of(
            TimeWindow.of("06:00", "11:00", 2), // morning
            TimeWindow.of("11:00", "14:00", 8), // lunch
            TimeWindow.of("14:00", "17:00", 5), // afternoon
            TimeWindow.of("17:00", "20:30", 10), // dinner
            TimeWindow.of("20:30", "23:00", 6)), // late dinner
        Score.of(1)); // night, 23:00–06:00
  }
}
