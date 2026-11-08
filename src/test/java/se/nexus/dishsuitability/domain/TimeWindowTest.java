package se.nexus.dishsuitability.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TimeWindowTest {

  @Nested
  @DisplayName("A window within one day (07:00–10:00)")
  class SameDayWindow {

    private final TimeWindow window = TimeWindow.of("07:00", "10:00", 10);

    @ParameterizedTest(name = "{0} -> inside={1}")
    @CsvSource({
      "06:59, false",
      "07:00, true", // start is inclusive
      "08:30, true",
      "09:59, true",
      "10:00, false", // end is exclusive, so adjacent windows never overlap
      "23:00, false"
    })
    void containsIsHalfOpen(String time, boolean expected) {
      assertThat(window.contains(LocalTime.parse(time))).isEqualTo(expected);
    }
  }

  @Nested
  @DisplayName("A window wrapping midnight (22:00–06:00)")
  class WrappingWindow {

    private final TimeWindow window = TimeWindow.of("22:00", "06:00", 1);

    @ParameterizedTest(name = "{0} -> inside={1}")
    @CsvSource({
      "21:59, false",
      "22:00, true",
      "23:59, true",
      "00:00, true",
      "05:59, true",
      "06:00, false",
      "12:00, false"
    })
    void containsWrapsPastMidnight(String time, boolean expected) {
      assertThat(window.contains(LocalTime.parse(time))).isEqualTo(expected);
    }
  }

  @Test
  void rejectsAnEmptyWindow() {
    assertThatThrownBy(() -> TimeWindow.of("10:00", "10:00", 5))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("must not be empty");
  }
}
