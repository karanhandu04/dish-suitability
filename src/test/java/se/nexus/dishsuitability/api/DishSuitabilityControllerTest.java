package se.nexus.dishsuitability.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalTime;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import se.nexus.dishsuitability.domain.Dish;
import se.nexus.dishsuitability.domain.Score;
import se.nexus.dishsuitability.service.DishSuitabilityService;

/**
 * Web-layer slice test: the controller, the exception handler and the servlet filters (API key,
 * request correlation) are loaded; the service is mocked. These tests are about HTTP behavior
 * (authentication, headers, status codes, validation, error format), not about scoring rules, which
 * have their own unit tests. Because the controller implements the interface generated from the
 * OpenAPI spec, they also check that the contract is honored.
 */
@WebMvcTest(
    controllers = DishSuitabilityController.class,
    properties = "app.security.api-keys=test-key,second-test-key")
class DishSuitabilityControllerTest {

  private static final String UUID_FORMAT =
      "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private DishSuitabilityService service;

  /** A request with a valid API key and channel. */
  private static MockHttpServletRequestBuilder validRequest(String body) {
    return scoreRequest(body, "test-key", "mobile-app");
  }

  /**
   * A scoring request with the given API key and channel; {@code null} leaves the header out.
   *
   * <p>Tests pass the values they want here rather than calling {@code .header(...)} again on
   * {@link #validRequest}: MockMvc's {@code header()} <em>adds</em> a value, it doesn't replace
   * one, and the server would read the first (valid) one.
   */
  private static MockHttpServletRequestBuilder scoreRequest(
      String body, String apiKey, String channel) {
    MockHttpServletRequestBuilder request =
        post("/dish-suitability").contentType(MediaType.APPLICATION_JSON).content(body);
    if (apiKey != null) {
      request.header(ApiHeaders.API_KEY, apiKey);
    }
    if (channel != null) {
      request.header(ApiHeaders.CHANNEL, channel);
    }
    return request;
  }

  private static final String PANCAKES_AT_0830 =
      """
      {"time": "08:30", "dish": "Pancakes"}
      """;

  @Nested
  class Scoring {

    @Test
    void returnsTheScoreForAValidRequest() throws Exception {
      when(service.score(Dish.PANCAKES, LocalTime.of(8, 30))).thenReturn(Score.of(10));

      mockMvc
          .perform(validRequest(PANCAKES_AT_0830))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.score").value(10));
    }

    @Test
    void mapsEveryDishInTheContract() throws Exception {
      when(service.score(Dish.ICE_CREAM, LocalTime.of(15, 0))).thenReturn(Score.of(10));

      mockMvc
          .perform(
              validRequest(
                  """
                  {"time": "15:00", "dish": "IceCream"}
                  """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.score").value(10));
    }
  }

  @Nested
  class Authentication {

    @Test
    void rejectsARequestWithoutAnApiKey() throws Exception {
      mockMvc
          .perform(scoreRequest(PANCAKES_AT_0830, null, "web"))
          .andExpect(status().isUnauthorized())
          .andExpect(header().string("WWW-Authenticate", "ApiKey"))
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(jsonPath("$.detail").value("API key is missing"));

      verifyNoInteractions(service);
    }

    @Test
    void rejectsAnUnknownApiKey() throws Exception {
      mockMvc
          .perform(scoreRequest(PANCAKES_AT_0830, "wrong-key", "web"))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.detail").value("API key is not valid"));

      verifyNoInteractions(service);
    }

    @Test
    void acceptsEveryConfiguredKeySoKeysCanBeRotated() throws Exception {
      when(service.score(any(), any())).thenReturn(Score.of(10));

      mockMvc
          .perform(scoreRequest(PANCAKES_AT_0830, "second-test-key", "web"))
          .andExpect(status().isOk());
    }
  }

  @Nested
  class RequestIdAndChannel {

    @Test
    void echoesTheCallersRequestId() throws Exception {
      when(service.score(any(), any())).thenReturn(Score.of(10));

      mockMvc
          .perform(validRequest(PANCAKES_AT_0830).header(ApiHeaders.REQUEST_ID, "abc-123"))
          .andExpect(status().isOk())
          .andExpect(header().string(ApiHeaders.REQUEST_ID, "abc-123"));
    }

    @Test
    void generatesARequestIdWhenTheCallerSendsNone() throws Exception {
      when(service.score(any(), any())).thenReturn(Score.of(10));

      mockMvc
          .perform(validRequest(PANCAKES_AT_0830))
          .andExpect(status().isOk())
          .andExpect(header().string(ApiHeaders.REQUEST_ID, matchesPattern(UUID_FORMAT)));
    }

    @Test
    void includesTheRequestIdInErrorResponses() throws Exception {
      mockMvc
          .perform(
              scoreRequest(PANCAKES_AT_0830, "wrong-key", "web")
                  .header(ApiHeaders.REQUEST_ID, "trace-me"))
          .andExpect(status().isUnauthorized())
          .andExpect(header().string(ApiHeaders.REQUEST_ID, "trace-me"))
          .andExpect(jsonPath("$.requestId").value("trace-me"));
    }

    @Test
    void rejectsAMissingChannel() throws Exception {
      mockMvc
          .perform(scoreRequest(PANCAKES_AT_0830, "test-key", null))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors['X-Channel']").value("X-Channel header is required"));

      verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "X-Channel \"{0}\" is rejected")
    @ValueSource(strings = {"has space", "semi;colon", "way-too-long-channel-name-over-32-chars"})
    void rejectsAnInvalidChannel(String channel) throws Exception {
      mockMvc
          .perform(scoreRequest(PANCAKES_AT_0830, "test-key", channel))
          .andExpect(status().isBadRequest())
          .andExpect(
              jsonPath("$.errors['X-Channel']")
                  .value("X-Channel must be 1-32 characters: letters, digits, '.', '_' or '-'"));

      verifyNoInteractions(service);
    }

    /** A header value with a newline could forge log lines; it must never be echoed or logged. */
    @Test
    void rejectsAnInvalidRequestIdAndDoesNotEchoIt() throws Exception {
      mockMvc
          .perform(
              validRequest(PANCAKES_AT_0830)
                  .header(ApiHeaders.REQUEST_ID, "abc\n2026-01-01 INFO forged log line"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors['X-Request-ID']").exists())
          .andExpect(header().string(ApiHeaders.REQUEST_ID, matchesPattern(UUID_FORMAT)));

      verifyNoInteractions(service);
    }
  }

  @Nested
  class Validation {

    @ParameterizedTest(name = "time \"{0}\" is rejected")
    @ValueSource(strings = {"8:30", "24:00", "12:60", "noon", "08:30:00", "0830"})
    void rejectsBadlyFormattedTimes(String time) throws Exception {
      mockMvc
          .perform(
              validRequest(
                  """
                  {"time": "%s", "dish": "Pancakes"}
                  """
                      .formatted(time)))
          .andExpect(status().isBadRequest())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
          .andExpect(
              jsonPath("$.errors.time").value("time must be in 24-hour HH:mm format, e.g. 08:30"));

      verifyNoInteractions(service);
    }

    @Test
    void reportsEveryMissingField() throws Exception {
      mockMvc
          .perform(validRequest("{}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.errors.time").value("time is required"))
          .andExpect(jsonPath("$.errors.dish").value("dish is required"));
    }

    @ParameterizedTest(name = "dish \"{0}\" is rejected")
    @ValueSource(strings = {"Pizza", "pancakes", "Ice Cream", ""})
    void rejectsADishNotInTheContractAndListsTheAcceptedOnes(String dish) throws Exception {
      mockMvc
          .perform(
              validRequest(
                  """
                  {"time": "12:00", "dish": "%s"}
                  """
                      .formatted(dish)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.title").value("Unsupported dish"))
          .andExpect(
              jsonPath("$.acceptedDishes", containsInAnyOrder("Pancakes", "Chicken", "IceCream")));

      verifyNoInteractions(service);
    }

    @Test
    void rejectsMalformedJson() throws Exception {
      mockMvc
          .perform(validRequest("{\"time\": \"08:30\", "))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.title").value("Malformed request"));
    }

    @Test
    void rejectsNonJsonContent() throws Exception {
      mockMvc
          .perform(validRequest("time=08:30").contentType(MediaType.TEXT_PLAIN))
          .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void rejectsGetOnTheScoringEndpoint() throws Exception {
      mockMvc
          .perform(get("/dish-suitability").header(ApiHeaders.API_KEY, "test-key"))
          .andExpect(status().isMethodNotAllowed());
    }
  }

  @Test
  void hidesInternalErrorsFromTheClient() throws Exception {
    when(service.score(any(), any()))
        .thenThrow(new IllegalStateException("database password is hunter2"));

    mockMvc
        .perform(validRequest(PANCAKES_AT_0830))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.detail").value("An unexpected error occurred"));
  }
}
