package se.nexus.dishsuitability.domain;

/**
 * A suitability score from 0 (not suitable at all) to 10 (perfect).
 *
 * <p>A value object: the 0–10 rule is enforced once, here, so no code path can produce an
 * out-of-range score.
 */
public record Score(int value) {

  public static final int MIN = 0;
  public static final int MAX = 10;

  public Score {
    if (value < MIN || value > MAX) {
      throw new IllegalArgumentException(
          "Score must be between %d and %d but was %d".formatted(MIN, MAX, value));
    }
  }

  public static Score of(int value) {
    return new Score(value);
  }
}
