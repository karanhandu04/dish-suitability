package se.nexus.dishsuitability.domain.scoring;

import java.util.List;
import org.springframework.stereotype.Component;
import se.nexus.dishsuitability.domain.Dish;
import se.nexus.dishsuitability.domain.Score;
import se.nexus.dishsuitability.domain.TimeWindow;
import se.nexus.dishsuitability.domain.TimeWindowDishScorer;

/** Ice cream: best in the afternoon and as an after-dinner dessert, not for breakfast. */
@Component
public class IceCreamScorer extends TimeWindowDishScorer {

  public IceCreamScorer() {
    super(
        Dish.ICE_CREAM,
        List.of(
            TimeWindow.of("08:00", "11:00", 2), // morning
            TimeWindow.of("11:00", "13:00", 6), // late morning / lunch
            TimeWindow.of("13:00", "17:00", 10), // afternoon
            TimeWindow.of("17:00", "19:00", 6), // early evening
            TimeWindow.of("19:00", "21:30", 8), // dessert after dinner
            TimeWindow.of("21:30", "23:30", 4)), // late evening
        Score.of(0)); // night and early morning
  }
}
