package se.nexus.dishsuitability.domain.scoring;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IceCreamScorerTest {

  private final IceCreamScorer scorer = new IceCreamScorer();

  @ParameterizedTest(name = "IceCream at {0} -> {1}")
  @CsvSource({
    "07:00, 0", // early morning default
    "08:00, 2",
    "12:00, 6",
    "13:00, 10", // afternoon (boundary)
    "16:59, 10",
    "17:00, 6",
    "19:00, 8", // dessert
    "21:29, 8",
    "21:30, 4",
    "23:29, 4",
    "23:30, 0", // default
    "02:00, 0"
  })
  void scoresByTimeOfDay(String time, int expected) {
    assertThat(scorer.score(LocalTime.parse(time)).value()).isEqualTo(expected);
  }
}
