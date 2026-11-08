package se.nexus.dishsuitability.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.nexus.dishsuitability.domain.Dish;
import se.nexus.dishsuitability.domain.DishScorer;
import se.nexus.dishsuitability.domain.Score;
import se.nexus.dishsuitability.domain.scoring.ChickenScorer;
import se.nexus.dishsuitability.domain.scoring.IceCreamScorer;
import se.nexus.dishsuitability.domain.scoring.PancakesScorer;

/** Plain unit tests: no Spring context, the service is constructed directly. */
class DishSuitabilityServiceTest {

  @Test
  void delegatesToTheScorerForTheRequestedDish() {
    DishSuitabilityService service =
        new DishSuitabilityService(
            List.of(
                fixedScorer(Dish.PANCAKES, 1),
                fixedScorer(Dish.CHICKEN, 2),
                fixedScorer(Dish.ICE_CREAM, 3)));

    assertThat(service.score(Dish.CHICKEN, LocalTime.NOON)).isEqualTo(Score.of(2));
    assertThat(service.score(Dish.ICE_CREAM, LocalTime.NOON)).isEqualTo(Score.of(3));
  }

  @Test
  void worksWithTheRealScorers() {
    DishSuitabilityService service =
        new DishSuitabilityService(
            List.of(new PancakesScorer(), new ChickenScorer(), new IceCreamScorer()));

    assertThat(service.score(Dish.PANCAKES, LocalTime.of(8, 0))).isEqualTo(Score.of(10));
  }

  @Test
  void failsFastWhenADishHasNoScorer() {
    assertThatThrownBy(() ->
            new DishSuitabilityService(
                List.of(fixedScorer(Dish.PANCAKES, 1), fixedScorer(Dish.CHICKEN, 2))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No scorer registered for")
        .hasMessageContaining("ICE_CREAM");
  }

  @Test
  void failsFastWhenADishHasTwoScorers() {
    assertThatThrownBy(() ->
            new DishSuitabilityService(
                List.of(
                    fixedScorer(Dish.PANCAKES, 1),
                    fixedScorer(Dish.PANCAKES, 9),
                    fixedScorer(Dish.CHICKEN, 2),
                    fixedScorer(Dish.ICE_CREAM, 3))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("More than one scorer registered for PANCAKES");
  }

  private static DishScorer fixedScorer(Dish dish, int score) {
    return new DishScorer() {
      @Override
      public Dish dish() {
        return dish;
      }

      @Override
      public Score score(LocalTime time) {
        return Score.of(score);
      }
    };
  }
}
