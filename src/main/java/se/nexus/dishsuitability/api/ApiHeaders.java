package se.nexus.dishsuitability.api;

import java.util.regex.Pattern;

/**
 * HTTP header names and formats shared by the filters and the error handler.
 *
 * <p>The formats mirror the {@code pattern}s in the OpenAPI spec. The contract validates them on
 * the scoring endpoint; the filters check them again because they run before that validation and
 * must never write an unvalidated header value into the logs.
 */
public final class ApiHeaders {

  public static final String API_KEY = "X-API-Key";
  public static final String CHANNEL = "X-Channel";
  public static final String REQUEST_ID = "X-Request-ID";

  public static final Pattern CHANNEL_FORMAT = Pattern.compile("[A-Za-z0-9._-]{1,32}");
  public static final Pattern REQUEST_ID_FORMAT = Pattern.compile("[A-Za-z0-9._-]{1,64}");

  private ApiHeaders() {
    throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
  }

  public static boolean isValid(String value, Pattern format) {
    return value != null && format.matcher(value).matches();
  }
}
