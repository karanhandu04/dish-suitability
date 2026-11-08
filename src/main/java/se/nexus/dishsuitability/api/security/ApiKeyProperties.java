package se.nexus.dishsuitability.api.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The API keys accepted by the service, bound from {@code app.security.api-keys}.
 *
 * <p>Several keys can be valid at the same time, so a key can be rotated without downtime: add the
 * new key, move callers over, then remove the old one. In an environment variable they are
 * comma-separated: {@code APP_SECURITY_API_KEYS=key-one,key-two}.
 *
 * <p>The application refuses to start without at least one key, so it can never run unprotected by
 * accident.
 */
@Validated
@ConfigurationProperties("app.security")
public record ApiKeyProperties(@NotEmpty List<@NotBlank String> apiKeys) {

  public ApiKeyProperties {
    apiKeys = apiKeys == null ? List.of() : List.copyOf(apiKeys);
  }

  @Override
  public String toString() {
    // Never print the keys, e.g. in a startup failure message or a debug log.
    return "ApiKeyProperties[apiKeys=<%d redacted>]".formatted(apiKeys.size());
  }
}
