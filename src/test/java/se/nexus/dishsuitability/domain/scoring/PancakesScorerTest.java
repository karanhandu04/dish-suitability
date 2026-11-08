package se.nexus.dishsuitability.domain.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PancakesScorerTest {

  private final PancakesScorer scorer = new PancakesScorer();

  @ParameterizedTest(name = "Pancakes at {0} -> {1}")
  @CsvSource({
    "03:00, 0", // night
    "06:00, 7", // early breakfast starts
    "07:00, 10", // breakfast starts (boundary)
    "08:30, 10",
    "09:59, 10",
    "10:00, 7", // brunch (boundary)
    "13:00, 3",
    "16:00, 5",
    "19:00, 2",
    "20:59, 2",
    "21:00, 0", // default kicks in
    "23:59, 0"
  })
  void scoresByTimeOfDay(String time, int expected) {
    assertThat(scorer.score(LocalTime.parse(time)).value()).isEqualTo(expected);
  }
}
