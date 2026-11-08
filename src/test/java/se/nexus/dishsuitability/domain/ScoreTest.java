package se.nexus.dishsuitability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ScoreTest {

  @ParameterizedTest
  @ValueSource(ints = {0, 5, 10})
  void acceptsValuesFromZeroToTen(int value) {
    assertThat(Score.of(value).value()).isEqualTo(value);
  }

  @ParameterizedTest
  @ValueSource(ints = {-1, 11, Integer.MAX_VALUE})
  void rejectsValuesOutsideZeroToTen(int value) {
    assertThatThrownBy(() -> Score.of(value))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("between 0 and 10");
  }
}
