package bi.gravy.ledger.common.errors;

import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every error into an RFC 7807 {@code application/problem+json} body with a stable {@code
 * code} property. Extending {@link ResponseEntityExceptionHandler} keeps Spring MVC's own errors
 * (malformed JSON, missing header, wrong HTTP method...) in the same format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(LedgerException.class)
  ProblemDetail handleLedgerException(LedgerException ex) {
    return problem(ex.code(), ex.getMessage());
  }

  /** Anything unexpected is a bug: log it with the stack trace, reveal nothing to the client. */
  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return problem(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred.");
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    ProblemDetail body = problem(ErrorCode.VALIDATION_FAILED, "Request body is invalid.");
    body.setProperty("errors", fieldErrors(ex.getBindingResult().getFieldErrors()));
    return handleExceptionInternal(ex, body, headers, status, request);
  }

  /**
   * Gives Spring MVC's own problem details a {@code code} too: any 400 is a validation error. The
   * body is inspected after {@code super} because Spring sometimes passes {@code null} and only
   * builds the problem detail there.
   */
  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception ex, Object body, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
    ResponseEntity<Object> response =
        super.handleExceptionInternal(ex, body, headers, status, request);
    if (response != null
        && response.getBody() instanceof ProblemDetail problem
        && !hasCode(problem)) {
      problem.setProperty("code", codeFor(status));
    }
    return response;
  }

  public static ProblemDetail problem(ErrorCode code, String detail) {
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
    problem.setTitle(code.title());
    problem.setProperty("code", code.name());
    return problem;
  }

  private static List<Map<String, String>> fieldErrors(List<FieldError> errors) {
    return errors.stream()
        .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
        .toList();
  }

  private static boolean hasCode(ProblemDetail problem) {
    return problem.getProperties() != null && problem.getProperties().containsKey("code");
  }

  private static String codeFor(HttpStatusCode status) {
    if (status.value() == HttpStatus.BAD_REQUEST.value()) {
      return ErrorCode.VALIDATION_FAILED.name();
    }
    HttpStatus known = HttpStatus.resolve(status.value());
    return known != null ? known.name() : ErrorCode.INTERNAL_ERROR.name();
  }
}
