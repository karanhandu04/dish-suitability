package se.nexus.dishsuitability.service;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import se.nexus.dishsuitability.domain.Dish;
import se.nexus.dishsuitability.domain.DishScorer;
import se.nexus.dishsuitability.domain.Score;

/**
 * Application service: picks the scorer for the requested dish and returns its score.
 *
 * <p>Spring injects every {@link DishScorer} bean. They are indexed by dish once, at startup, and
 * the constructor fails fast if a dish has no scorer or two scorers. A misconfiguration therefore
 * stops the application from starting rather than surfacing as a 500 error in production.
 */
@Slf4j
@Service
public class DishSuitabilityService {

  private final Map<Dish, DishScorer> scorersByDish;

  public DishSuitabilityService(Collection<DishScorer> scorers) {
    this.scorersByDish = indexByDish(scorers);
    log.info("Registered scorers for dishes {}", scorersByDish.keySet());
  }

  public Score score(@NonNull Dish dish, @NonNull LocalTime time) {
    Score score = scorersByDish.get(dish).score(time);
    log.debug("Scored dish={} time={} score={}", dish, time, score.value());
    return score;
  }

  private static Map<Dish, DishScorer> indexByDish(Collection<DishScorer> scorers) {
    Map<Dish, DishScorer> index = new EnumMap<>(Dish.class);
    for (DishScorer scorer : scorers) {
      DishScorer previous = index.putIfAbsent(scorer.dish(), scorer);
      if (previous != null) {
        throw new IllegalStateException(
            "More than one scorer registered for "
                + scorer.dish()
                + ": "
                + previous.getClass().getSimpleName()
                + " and "
                + scorer.getClass().getSimpleName());
      }
    }
    List<Dish> missing =
        Arrays.stream(Dish.values()).filter(dish -> !index.containsKey(dish)).toList();
    if (!missing.isEmpty()) {
      throw new IllegalStateException("No scorer registered for: " + missing);
    }
    // EnumMap (not Map.copyOf) keeps the dishes in declaration order for the startup log.
    return new EnumMap<>(index);
  }
}
