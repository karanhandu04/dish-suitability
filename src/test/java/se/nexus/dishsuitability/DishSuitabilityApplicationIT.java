package se.nexus.dishsuitability;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * End-to-end test: starts the real application on a random port, with the real scorers, filters and
 * configuration, and calls it over HTTP. Runs in the Maven `verify` phase (Failsafe), after the
 * fast unit tests.
 */
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.security.api-keys=it-key")
class DishSuitabilityApplicationIT {

  @Value("${local.server.port}")
  private int port;

  private RestTestClient http;

  @BeforeEach
  void setUp() {
    http = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
  }

  @Test
  void scoresADishEndToEnd() {
    http.post()
        .uri("/dish-suitability")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-API-Key", "it-key")
        .header("X-Channel", "integration-test")
        .header("X-Request-ID", "it-request-1")
        .body(
            """
            {"time": "18:00", "dish": "Chicken"}
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueEquals("X-Request-ID", "it-request-1")
        .expectBody()
        .jsonPath("$.score")
        .isEqualTo(10);
  }

  @Test
  void rejectsACallWithoutAnApiKey() {
    http.post()
        .uri("/dish-suitability")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-Channel", "integration-test")
        .body(
            """
            {"time": "18:00", "dish": "Chicken"}
            """)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectHeader()
        .contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON);
  }

  @Test
  void rejectsAnUnsupportedDishEndToEnd() {
    http.post()
        .uri("/dish-suitability")
        .contentType(MediaType.APPLICATION_JSON)
        .header("X-API-Key", "it-key")
        .header("X-Channel", "integration-test")
        .body(
            """
            {"time": "18:00", "dish": "Pizza"}
            """)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectBody()
        .jsonPath("$.title")
        .isEqualTo("Unsupported dish");
  }

  @Test
  void healthEndpointIsPublicAndReportsUp() {
    http.get()
        .uri("/health")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo("UP");
  }

  @Test
  void publishesItsOpenApiContractWithoutAuthentication() {
    http.get()
        .uri("/openapi/dish-suitability.yaml")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody(String.class)
        .value(spec -> assertThat(spec).contains("openapi: 3.0.3").contains("/dish-suitability"));
  }
}
