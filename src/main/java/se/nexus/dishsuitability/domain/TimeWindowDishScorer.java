package se.nexus.dishsuitability.domain;

import java.time.LocalTime;
import java.util.List;
import lombok.NonNull;

/**
 * Base class for scorers defined as a list of time windows plus a default score.
 *
 * <p>Each concrete dish only declares its data (windows and default). The lookup logic lives here
 * once. Windows are checked in order and the first match wins; any time not covered by a window
 * gets the default score.
 */
public abstract class TimeWindowDishScorer implements DishScorer {

  private final Dish dish;
  private final List<TimeWindow> windows;
  private final Score defaultScore;

  // Lombok's @NonNull generates the null checks, so a misconfigured scorer fails at startup.
  protected TimeWindowDishScorer(
      @NonNull Dish dish, @NonNull List<TimeWindow> windows, @NonNull Score defaultScore) {
    this.dish = dish;
    this.windows = List.copyOf(windows);
    this.defaultScore = defaultScore;
  }

  @Override
  public Dish dish() {
    return dish;
  }

  @Override
  public Score score(@NonNull LocalTime time) {
    return windows.stream()
        .filter(window -> window.contains(time))
        .map(TimeWindow::score)
        .findFirst()
        .orElse(defaultScore);
  }
}
