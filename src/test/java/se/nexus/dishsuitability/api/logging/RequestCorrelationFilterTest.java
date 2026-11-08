package se.nexus.dishsuitability.api.logging;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import se.nexus.dishsuitability.api.ApiHeaders;

/** Plain unit test of the filter, without a Spring context. */
class RequestCorrelationFilterTest {

  private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

  @Test
  void putsRequestIdAndChannelIntoTheLoggingContextDuringTheRequest() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/dish-suitability");
    request.addHeader(ApiHeaders.REQUEST_ID, "req-1");
    request.addHeader(ApiHeaders.CHANNEL, "web");
    AtomicReference<String> requestIdSeen = new AtomicReference<>();
    AtomicReference<String> channelSeen = new AtomicReference<>();
    FilterChain chain =
        (_, _) -> {
          requestIdSeen.set(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID));
          channelSeen.set(MDC.get(RequestCorrelationFilter.MDC_CHANNEL));
        };

    filter.doFilter(request, new MockHttpServletResponse(), chain);

    assertThat(requestIdSeen).hasValue("req-1");
    assertThat(channelSeen).hasValue("web");
  }

  @Test
  void clearsTheLoggingContextAfterwardsBecauseThreadsAreReused() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/dish-suitability");
    request.addHeader(ApiHeaders.REQUEST_ID, "req-1");

    filter.doFilter(request, new MockHttpServletResponse(), (_, _) -> {});

    assertThat(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID)).isNull();
    assertThat(MDC.get(RequestCorrelationFilter.MDC_CHANNEL)).isNull();
  }

  @Test
  void neverLogsAnUnsafeHeaderValue() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/dish-suitability");
    request.addHeader(ApiHeaders.REQUEST_ID, "x\nFORGED LOG LINE");
    request.addHeader(ApiHeaders.CHANNEL, "web\nFORGED");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> requestIdSeen = new AtomicReference<>();
    AtomicReference<String> channelSeen = new AtomicReference<>();

    filter.doFilter(
        request,
        response,
        (_, _) -> {
          requestIdSeen.set(MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID));
          channelSeen.set(MDC.get(RequestCorrelationFilter.MDC_CHANNEL));
        });

    assertThat(requestIdSeen.get()).doesNotContain("FORGED").hasSize(36); // a generated UUID
    assertThat(response.getHeader(ApiHeaders.REQUEST_ID)).isEqualTo(requestIdSeen.get());
    assertThat(channelSeen).hasValue("invalid");
  }
}
