package bi.gravy.ledger.security;

import bi.gravy.ledger.common.errors.ErrorCode;
import bi.gravy.ledger.common.errors.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 401 and 403 decided by the security filter chain happen before any controller runs, so the
 * {@code @RestControllerAdvice} never sees them. This writes the same problem+json format.
 */
@Component
public class ProblemDetailSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  public ProblemDetailSecurityHandler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /** No token, or an invalid/expired one. */
  @Override
  public void commence(
      HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
      throws IOException {
    response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
    write(
        request,
        response,
        GlobalExceptionHandler.problem(
            ErrorCode.UNAUTHENTICATED, "A valid bearer token is required."));
  }

  /** Valid token, but the role does not allow this endpoint. */
  @Override
  public void handle(
      HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
      throws IOException {
    write(
        request,
        response,
        GlobalExceptionHandler.problem(
            ErrorCode.ACCESS_DENIED, "You are not allowed to perform this operation."));
  }

  private void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail body)
      throws IOException {
    body.setInstance(URI.create(request.getRequestURI()));
    response.setStatus(body.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
