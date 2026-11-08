package se.nexus.dishsuitability.api.security;

import lombok.Getter;

/** The request had no API key, or a key that is not accepted. */
@Getter
public class ApiKeyAuthenticationException extends RuntimeException {

  private final boolean keyMissing;

  public ApiKeyAuthenticationException(boolean keyMissing) {
    super(keyMissing ? "API key is missing" : "API key is not valid");
    this.keyMissing = keyMissing;
  }
}
