package se.nexus.dishsuitability.api.logging;

import static se.nexus.dishsuitability.api.ApiHeaders.CHANNEL;
import static se.nexus.dishsuitability.api.ApiHeaders.CHANNEL_FORMAT;
import static se.nexus.dishsuitability.api.ApiHeaders.REQUEST_ID;
import static se.nexus.dishsuitability.api.ApiHeaders.REQUEST_ID_FORMAT;
import static se.nexus.dishsuitability.api.ApiHeaders.isValid;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * First filter in the chain. For every request it:
 *
 * <ol>
 *   <li>takes the caller's {@code X-Request-ID}, or generates one, and returns it in the response;
 *   <li>puts the request id and {@code X-Channel} into the SLF4J MDC, so every log line written
 *       while handling the request carries them (see {@code logging.pattern.level});
 *   <li>writes one access-log line with method, path, status and duration.
 * </ol>
 *
 * <p>Header values are only logged if they match the expected format, which prevents log injection
 * (e.g. a header containing a newline and a fake log line).
 */
@Slf4j
@Component
@Order(RequestCorrelationFilter.ORDER)
public class RequestCorrelationFilter extends OncePerRequestFilter {

  public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

  public static final String MDC_REQUEST_ID = "requestId";
  public static final String MDC_CHANNEL = "channel";

  private static final Set<String> HEALTH_PATHS =
      Set.of("/health", "/health/liveness", "/health/readiness");

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String requestId = request.getHeader(REQUEST_ID);
    if (!isValid(requestId, REQUEST_ID_FORMAT)) {
      requestId = UUID.randomUUID().toString();
    }
    String channel = request.getHeader(CHANNEL);
    if (!isValid(channel, CHANNEL_FORMAT)) {
      channel = channel == null ? "-" : "invalid";
    }

    response.setHeader(REQUEST_ID, requestId);
    MDC.put(MDC_REQUEST_ID, requestId);
    MDC.put(MDC_CHANNEL, channel);
    long start = System.nanoTime();
    try {
      chain.doFilter(request, response);
    } finally {
      logAccess(request, response, (System.nanoTime() - start) / 1_000_000);
      // Threads are pooled: without this, the next request on this thread would inherit our ids.
      MDC.remove(MDC_REQUEST_ID);
      MDC.remove(MDC_CHANNEL);
    }
  }

  private static void logAccess(
      HttpServletRequest request, HttpServletResponse response, long durationMs) {
    // Health checks run every few seconds; keep them out of the INFO log.
    if (HEALTH_PATHS.contains(request.getRequestURI())) {
      log.debug(
          "{} {} -> {} ({} ms)",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          durationMs);
    } else {
      log.info(
          "{} {} -> {} ({} ms)",
          request.getMethod(),
          request.getRequestURI(),
          response.getStatus(),
          durationMs);
    }
  }
}
