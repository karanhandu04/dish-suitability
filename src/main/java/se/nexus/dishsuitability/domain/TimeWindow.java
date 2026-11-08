package se.nexus.dishsuitability.domain;

import java.time.LocalTime;
import java.util.Objects;

/**
 * A half-open time-of-day range {@code [start, end)} with the score a dish gets inside it.
 *
 * <p>If {@code end} is before {@code start}, the window wraps past midnight, so {@code 22:00–06:00}
 * covers 22:00 up to (not including) 06:00 the next morning. Half-open ranges mean adjacent windows
 * such as 07:00–10:00 and 10:00–12:00 never overlap.
 */
public record TimeWindow(LocalTime start, LocalTime end, Score score) {

  public TimeWindow {
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    Objects.requireNonNull(score, "score");
    if (start.equals(end)) {
      throw new IllegalArgumentException("A time window must not be empty: " + start + "–" + end);
    }
  }

  /** Convenience factory: {@code TimeWindow.of("07:00", "10:00", 10)}. */
  public static TimeWindow of(String start, String end, int score) {
    return new TimeWindow(LocalTime.parse(start), LocalTime.parse(end), Score.of(score));
  }

  public boolean contains(LocalTime time) {
    Objects.requireNonNull(time, "time");
    if (start.isBefore(end)) {
      return !time.isBefore(start) && time.isBefore(end);
    }
    // wraps past midnight
    return !time.isBefore(start) || time.isBefore(end);
  }
}
