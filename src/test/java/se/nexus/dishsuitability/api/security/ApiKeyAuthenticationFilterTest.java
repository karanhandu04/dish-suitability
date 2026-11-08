package se.nexus.dishsuitability.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;

/** Which paths skip authentication. Plain unit test, no Spring context. */
class ApiKeyAuthenticationFilterTest {

  private final ApiKeyAuthenticationFilter filter =
      new ApiKeyAuthenticationFilter(
          new ApiKeyProperties(List.of("test-key")), (request, response, handler, ex) -> null);

  @ParameterizedTest(name = "{0} -> public={1}")
  @CsvSource({
    "/health,                              true",
    "/health/liveness,                     true",
    "/health/readiness,                    true",
    "/openapi/dish-suitability.yaml,       true",
    "/dish-suitability,                    false",
    // Look-alikes must NOT be public: exact matching, not a prefix check
    "/healthz,                             false",
    "/health/,                             false",
    "/health/../dish-suitability,          false",
    "/health;x/../dish-suitability,        false",
    "/openapi/other.yaml,                  false",
    "/openapi/../dish-suitability,         false",
    "/openapi/%2e%2e/dish-suitability,     false"
  })
  void onlyTheListedPathsArePublic(String path, boolean expectedPublic) {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", path);

    assertThat(filter.shouldNotFilter(request)).isEqualTo(expectedPublic);
  }
}
