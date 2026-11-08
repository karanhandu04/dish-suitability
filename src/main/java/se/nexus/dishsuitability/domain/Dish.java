package se.nexus.dishsuitability.domain;

/**
 * The dishes the service knows how to score.
 *
 * <p>The API contract defines which dishes callers may send; this enum is the domain's own list.
 * The controller maps one to the other with an exhaustive switch, so adding a dish to the contract
 * fails the build until it is added here, and the application refuses to start until it also has a
 * {@link DishScorer}.
 */
public enum Dish {
  PANCAKES,
  CHICKEN,
  ICE_CREAM
}
