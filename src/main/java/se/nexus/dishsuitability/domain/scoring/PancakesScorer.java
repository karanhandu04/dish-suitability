package se.nexus.dishsuitability.domain.scoring;

import java.util.List;
import org.springframework.stereotype.Component;
import se.nexus.dishsuitability.domain.Dish;
import se.nexus.dishsuitability.domain.Score;
import se.nexus.dishsuitability.domain.TimeWindow;
import se.nexus.dishsuitability.domain.TimeWindowDishScorer;

/** Pancakes: a breakfast dish, still fine for brunch or an afternoon treat, poor at night. */
@Component
public class PancakesScorer extends TimeWindowDishScorer {

  public PancakesScorer() {
    super(
        Dish.PANCAKES,
        List.of(
            TimeWindow.of("06:00", "07:00", 7), // early breakfast
            TimeWindow.of("07:00", "10:00", 10), // breakfast
            TimeWindow.of("10:00", "12:00", 7), // brunch
            TimeWindow.of("12:00", "15:00", 3), // lunch time
            TimeWindow.of("15:00", "17:00", 5), // afternoon treat
            TimeWindow.of("17:00", "21:00", 2)), // dinner / evening
        Score.of(0)); // night, 21:00–06:00
  }
}
