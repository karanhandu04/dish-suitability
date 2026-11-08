package se.nexus.dishsuitability.domain.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ChickenScorerTest {

  private final ChickenScorer scorer = new ChickenScorer();

  @ParameterizedTest(name = "Chicken at {0} -> {1}")
  @CsvSource({
    "00:30, 1", // night default
    "05:59, 1",
    "06:00, 2", // morning
    "10:59, 2",
    "11:00, 8", // lunch (boundary)
    "12:30, 8",
    "15:00, 5",
    "17:00, 10", // dinner (boundary)
    "20:29, 10",
    "20:30, 6", // late dinner
    "22:59, 6",
    "23:00, 1" // back to default
  })
  void scoresByTimeOfDay(String time, int expected) {
    assertThat(scorer.score(LocalTime.parse(time)).value()).isEqualTo(expected);
  }
}
