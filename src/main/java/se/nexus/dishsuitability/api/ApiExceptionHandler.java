package se.nexus.dishsuitability.api;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.AnnotatedMethod;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import se.nexus.dishsuitability.api.generated.model.Dish;
import se.nexus.dishsuitability.api.logging.RequestCorrelationFilter;
import se.nexus.dishsuitability.api.security.ApiKeyAuthenticationException;
import tools.jackson.core.JacksonException;

/**
 * Turns exceptions into consistent RFC 7807 "problem detail" responses ({@code
 * application/problem+json}), so every error has the same shape for API clients.
 *
 * <p>Every problem carries the {@code requestId}, so a caller reporting an error gives us the id to
 * find it in the logs.
 *
 * <p>Extending {@link ResponseEntityExceptionHandler} gives standard Spring MVC errors (wrong HTTP
 * method, unsupported media type, ...) the same format for free.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

  /**
   * Readable messages for format rules. The constraints are generated from the OpenAPI spec, which
   * has no way to attach a custom message, so the defaults ("must match ...") are replaced here.
   */
  private static final Map<String, String> FORMAT_HINTS =
      Map.of(
          "time",
          "time must be in 24-hour HH:mm format, e.g. 08:30",
          ApiHeaders.CHANNEL,
          ApiHeaders.CHANNEL + " must be 1-32 characters: letters, digits, '.', '_' or '-'",
          ApiHeaders.REQUEST_ID,
          ApiHeaders.REQUEST_ID + " must be 1-64 characters: letters, digits, '.', '_' or '-'");

  /**
   * Invalid headers (e.g. a malformed {@code X-Channel}).
   *
   * <p>The generated API interface is annotated with {@code @Validated}, so Spring Boot validates
   * the controller's parameters through a proxy. With {@code
   * spring.validation.method.adapt-constraint-violations=true} that proxy reports failures as a
   * {@link MethodValidationException}, which Spring treats as a server error (500) by default.
   * Raised for a controller, it is the caller's input that is wrong, so it becomes a 400 here.
   */
  @Override
  protected ResponseEntity<Object> handleMethodValidationException(
      MethodValidationException ex, HttpHeaders headers, HttpStatus status, WebRequest request) {

    boolean controllerInput =
        !ex.isForReturnValue()
            && AnnotatedElementUtils.hasAnnotation(ex.getTarget().getClass(), RestController.class);
    if (!controllerInput) {
      log.error("Method validation failed", ex);
      return ResponseEntity.internalServerError()
          .body(
              problem(
                  HttpStatus.INTERNAL_SERVER_ERROR,
                  "Internal server error",
                  "An unexpected error occurred"));
    }
    return validationProblem(errorsOf(ex.getParameterValidationResults()));
  }

  /** The same failures, when Spring MVC's built-in method validation reports them instead. */
  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return validationProblem(errorsOf(ex.getParameterValidationResults()));
  }

  /** Request body validation failures (checked before the headers). */
  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {

    Map<String, String> errors = new LinkedHashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(e -> errors.putIfAbsent(e.getField(), messageFor(e)));
    return validationProblem(errors);
  }

  /** A required header is missing (e.g. {@code X-Channel}). */
  @Override
  protected ResponseEntity<Object> handleServletRequestBindingException(
      ServletRequestBindingException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {

    if (ex instanceof MissingRequestHeaderException missing) {
      String header = missing.getHeaderName();
      return validationProblem(Map.of(header, header + " header is required"));
    }
    return super.handleServletRequestBindingException(ex, headers, status, request);
  }

  /**
   * Missing body, malformed JSON, or a value Jackson cannot convert. The most common case, a dish
   * that isn't in the contract's enum, gets a dedicated message listing the accepted dishes.
   */
  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {

    Optional<String> property = invalidProperty(ex);
    if (property.filter("dish"::equals).isPresent()) {
      ProblemDetail problem =
          problem(
              HttpStatus.BAD_REQUEST,
              "Unsupported dish",
              "dish must be one of: " + String.join(", ", acceptedDishes()));
      problem.setProperty("acceptedDishes", acceptedDishes());
      return ResponseEntity.badRequest().body(problem);
    }
    return property
        .map(s -> validationProblem(Map.of(s, s + " has an invalid value")))
        .orElseGet(
            () ->
                ResponseEntity.badRequest()
                    .body(
                        problem(
                            HttpStatus.BAD_REQUEST,
                            "Malformed request",
                            "Request body is missing or is not valid JSON")));
  }

  @ExceptionHandler(ApiKeyAuthenticationException.class)
  public ResponseEntity<ProblemDetail> handleApiKey(ApiKeyAuthenticationException ex) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .header(HttpHeaders.WWW_AUTHENTICATE, "ApiKey")
        .body(problem(HttpStatus.UNAUTHORIZED, "Unauthorized", ex.getMessage()));
  }

  /** Anything unexpected: log it with the stack trace, but never leak internals to the client. */
  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unexpected error", ex);
    return problem(
        HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error", "An unexpected error occurred");
  }

  /** Adds the request id to problems created by the base class (405, 415, ...). */
  @Override
  protected ResponseEntity<Object> createResponseEntity(
      Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
    if (body instanceof ProblemDetail problem) {
      withRequestId(problem);
    }
    return super.createResponseEntity(body, headers, statusCode, request);
  }

  private static ResponseEntity<Object> validationProblem(Map<String, String> errors) {
    ProblemDetail problem =
        problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request validation failed");
    problem.setProperty("errors", errors);
    return ResponseEntity.badRequest().body(problem);
  }

  private static ProblemDetail problem(HttpStatus status, String title, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
    problem.setTitle(title);
    return withRequestId(problem);
  }

  private static ProblemDetail withRequestId(ProblemDetail problem) {
    String requestId = MDC.get(RequestCorrelationFilter.MDC_REQUEST_ID);
    if (requestId != null) {
      problem.setProperty("requestId", requestId);
    }
    return problem;
  }

  private static Map<String, String> errorsOf(List<ParameterValidationResult> results) {
    Map<String, String> errors = new LinkedHashMap<>();
    for (ParameterValidationResult result : results) {
      if (result instanceof ParameterErrors bodyErrors) {
        bodyErrors.getFieldErrors().forEach(e -> errors.putIfAbsent(e.getField(), messageFor(e)));
      } else {
        String name = parameterName(result.getMethodParameter());
        result.getResolvableErrors().stream()
            .findFirst()
            .ifPresent(e -> errors.putIfAbsent(name, messageFor(name, e)));
      }
    }
    return errors;
  }

  private static String messageFor(FieldError error) {
    return messageFor(error.getField(), error);
  }

  private static String messageFor(String name, MessageSourceResolvable error) {
    String[] codes = error.getCodes();
    // The last code is the bare constraint name, e.g. "Pattern" or "NotNull".
    String constraint = codes == null || codes.length == 0 ? "" : codes[codes.length - 1];
    return switch (constraint) {
      case "NotNull", "NotBlank" -> name + " is required";
      case "Size" -> name + " must not be empty";
      case "Pattern" -> FORMAT_HINTS.getOrDefault(name, name + " has an invalid format");
      default -> name + " " + error.getDefaultMessage();
    };
  }

  /**
   * The header name from {@code @RequestHeader} (e.g. {@code X-Channel}) if present, otherwise the
   * Java parameter name. {@link AnnotatedMethod} also finds annotations declared on the generated
   * interface, not just on the implementing method.
   */
  private static String parameterName(MethodParameter parameter) {
    MethodParameter annotated =
        new AnnotatedMethod(parameter.getMethod())
            .getMethodParameters()[parameter.getParameterIndex()];
    RequestHeader header = annotated.getParameterAnnotation(RequestHeader.class);
    if (header != null && !header.value().isEmpty()) {
      return header.value();
    }
    return parameter.getParameterName();
  }

  /** The JSON property Jackson failed on, if any, e.g. {@code "dish"}. */
  private static Optional<String> invalidProperty(Throwable ex) {
    for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
      if (cause instanceof JacksonException jackson && !jackson.getPath().isEmpty()) {
        return Optional.ofNullable(jackson.getPath().getLast().getPropertyName());
      }
    }
    return Optional.empty();
  }

  /** The accepted values come from the contract's enum, so they can never be out of date. */
  private static List<String> acceptedDishes() {
    return Arrays.stream(Dish.values()).map(Dish::getValue).toList();
  }
}
