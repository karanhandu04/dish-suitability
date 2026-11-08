package se.nexus.dishsuitability.api;

import java.time.LocalTime;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import se.nexus.dishsuitability.api.generated.DishSuitabilityApi;
import se.nexus.dishsuitability.api.generated.model.DishSuitabilityRequest;
import se.nexus.dishsuitability.api.generated.model.DishSuitabilityResponse;
import se.nexus.dishsuitability.domain.Dish;
import se.nexus.dishsuitability.service.DishSuitabilityService;

/**
 * Implements the interface generated from the OpenAPI spec ({@code
 * src/main/resources/static/openapi/dish-suitability.yaml}).
 *
 * <p>The path, HTTP method, content types, headers and validation annotations all come from the
 * generated {@link DishSuitabilityApi}, so they always match the contract. This class only
 * translates between the API models and the domain: generated classes never go deeper than here.
 */
@RestController
@RequiredArgsConstructor
public class DishSuitabilityController implements DishSuitabilityApi {

  private final DishSuitabilityService service;

  /**
   * {@code xChannel} and {@code xRequestId} are validated by the contract. They are not used here:
   * {@code RequestCorrelationFilter} has already put them into the logging context.
   */
  @Override
  public ResponseEntity<DishSuitabilityResponse> scoreDish(
      String xChannel, DishSuitabilityRequest request, String xRequestId) {
    var dish = toDomain(request.getDish());
    // Format already validated against the spec's pattern, so parsing cannot fail here.
    var time = LocalTime.parse(request.getTime());

    var score = service.score(dish, time);
    return ResponseEntity.ok(new DishSuitabilityResponse(score.value()));
  }

  /**
   * Exhaustive switch with no {@code default}: if a dish is added to the contract, this stops
   * compiling until the domain supports it. Contract, domain and scorers cannot drift apart.
   */
  static Dish toDomain(se.nexus.dishsuitability.api.generated.model.Dish apiDish) {
    return switch (apiDish) {
      case PANCAKES -> Dish.PANCAKES;
      case CHICKEN -> Dish.CHICKEN;
      case ICE_CREAM -> Dish.ICE_CREAM;
    };
  }
}
