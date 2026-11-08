package se.nexus.dishsuitability.domain.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import se.nexus.dishsuitability.domain.Dish;
import se.nexus.dishsuitability.domain.DishScorer;
import se.nexus.dishsuitability.domain.Score;

/**
 * Rules every scorer must follow, checked for all of them at once. A new scorer only needs to be
 * added to {@link #scorers()} to be covered.
 */
class AllScorersContractTest {

  static Stream<DishScorer> scorers() {
    return Stream.of(new PancakesScorer(), new ChickenScorer(), new IceCreamScorer());
  }

  @ParameterizedTest(name = "{0} returns a 0–10 score for every minute of the day")
  @MethodSource("scorers")
  void returnsAValidScoreForEveryMinuteOfTheDay(DishScorer scorer) {
    for (int minute = 0; minute < 24 * 60; minute++) {
      Score score = scorer.score(LocalTime.MIDNIGHT.plusMinutes(minute));
      assertThat(score.value()).isBetween(Score.MIN, Score.MAX);
    }
  }

  @Test
  void thereIsExactlyOneScorerForEveryDish() {
    List<Dish> covered = scorers().map(DishScorer::dish).sorted().toList();
    assertThat(covered).containsExactlyElementsOf(Arrays.asList(Dish.values()));
  }
}
