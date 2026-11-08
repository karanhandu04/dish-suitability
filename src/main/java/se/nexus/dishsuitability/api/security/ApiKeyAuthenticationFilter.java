package se.nexus.dishsuitability.api.security;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import se.nexus.dishsuitability.api.ApiHeaders;
import se.nexus.dishsuitability.api.logging.RequestCorrelationFilter;

/**
 * Requires a valid {@code X-API-Key} header on every request except the public paths (health and
 * the published API contract). Secure by default: a new endpoint is protected automatically.
 *
 * <p>Design choices:
 *
 * <ul>
 *   <li>Keys are compared as SHA-256 hashes with {@link MessageDigest#isEqual}, which takes the
 *       same time whether the first or the last character differs. A plain {@code equals} returns
 *       early, and the timing difference can leak the key one character at a time.
 *   <li>Only hashes are kept in memory, and keys are never logged.
 *   <li>Rejections are handed to the MVC exception handling, so a 401 has the same problem+json
 *       format as every other error.
 * </ul>
 *
 * <p>This is deliberately a plain servlet filter: the brief asks for basic API key auth. For OAuth2
 * / JWT, I would switch to Spring Security's resource server support.
 */
@Slf4j
@Component
@Order(RequestCorrelationFilter.ORDER + 10) // after the request id is set, so 401s are traceable
@EnableConfigurationProperties(ApiKeyProperties.class)
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

  /**
   * The only paths reachable without an API key, matched exactly. Exact matching (not "starts
   * with") means a crafted path such as {@code /health/../dish-suitability} or {@code
   * /openapi/anything} is never treated as public, whatever the framework later routes it to.
   */
  public static final Set<String> PUBLIC_PATHS =
      Set.of("/health", "/health/liveness", "/health/readiness", "/openapi/dish-suitability.yaml");

  private final List<byte[]> validKeyHashes;
  private final HandlerExceptionResolver exceptionResolver;

  public ApiKeyAuthenticationFilter(
      ApiKeyProperties properties,
      @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
    this.validKeyHashes =
        properties.apiKeys().stream().map(ApiKeyAuthenticationFilter::sha256).toList();
    this.exceptionResolver = exceptionResolver;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return PUBLIC_PATHS.contains(request.getRequestURI());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {

    String key = request.getHeader(ApiHeaders.API_KEY);
    if (isAccepted(key)) {
      chain.doFilter(request, response);
      return;
    }
    boolean missing = key == null || key.isBlank();
    log.warn("Rejected request: API key {}", missing ? "missing" : "not valid");
    exceptionResolver.resolveException(
        request, response, null, new ApiKeyAuthenticationException(missing));
  }

  private boolean isAccepted(String key) {
    if (key == null || key.isBlank()) {
      return false;
    }
    byte[] candidate = sha256(key);
    boolean accepted = false;
    // Check every key (no early exit), so timing doesn't reveal which key was close.
    for (byte[] valid : validKeyHashes) {
      accepted |= MessageDigest.isEqual(valid, candidate);
    }
    return accepted;
  }

  private static byte[] sha256(String value) {
    try {
      return MessageDigest.getInstance("SHA-256").digest(value.getBytes(UTF_8));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
    }
  }
}
