package se.nexus.dishsuitability.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import se.nexus.dishsuitability.api.generated.model.Dish;

/** Every dish in the API contract maps to the domain dish of the same name. */
class DishMappingTest {

  @ParameterizedTest
  @EnumSource(Dish.class)
  void mapsEveryContractDishToTheDomain(Dish apiDish) {
    assertThat(DishSuitabilityController.toDomain(apiDish).name()).isEqualTo(apiDish.name());
  }
}
