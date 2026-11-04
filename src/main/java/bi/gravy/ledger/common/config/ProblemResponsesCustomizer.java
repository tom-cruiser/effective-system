package bi.gravy.ledger.common.config;

import bi.gravy.ledger.common.errors.ApiErrors;
import bi.gravy.ledger.common.errors.ErrorCode;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Adds the error responses of every operation to the OpenAPI document, each with a realistic
 * problem+json example:
 *
 * <ul>
 *   <li>{@code 400 VALIDATION_FAILED} for any operation that takes input;
 *   <li>{@code 401 UNAUTHENTICATED} for any operation that needs a token;
 *   <li>the codes listed in the method's {@link ApiErrors}.
 * </ul>
 */
@Component
class ProblemResponsesCustomizer implements OperationCustomizer {

  private static final String PROBLEM_JSON = "application/problem+json";

  /** What the API really says in {@code detail} for each code. */
  private static final Map<ErrorCode, String> SAMPLE_DETAILS =
      Map.ofEntries(
          Map.entry(ErrorCode.VALIDATION_FAILED, "Request body is invalid."),
          Map.entry(ErrorCode.UNAUTHENTICATED, "A valid bearer token is required."),
          Map.entry(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect"),
          Map.entry(ErrorCode.ACCESS_DENIED, "You are not allowed to access this account."),
          Map.entry(
              ErrorCode.ACCOUNT_NOT_FOUND,
              "Account 0b6f1c1e-6a52-4c3e-9a51-2f1d8d7c4a10 not found"),
          Map.entry(
              ErrorCode.TRANSACTION_NOT_FOUND,
              "Transaction 5e1d7a3c-2b4f-4c6e-9d8a-1f0e3c5b7a92 not found"),
          Map.entry(
              ErrorCode.IDEMPOTENCY_CONFLICT,
              "Idempotency-Key was already used for a different request"),
          Map.entry(
              ErrorCode.ALREADY_REVERSED,
              "Transaction 5e1d7a3c-2b4f-4c6e-9d8a-1f0e3c5b7a92 is already reversed"),
          Map.entry(
              ErrorCode.INSUFFICIENT_FUNDS,
              "Account 0b6f1c1e-6a52-4c3e-9a51-2f1d8d7c4a10 has insufficient funds"),
          Map.entry(
              ErrorCode.CURRENCY_MISMATCH,
              "Account 0b6f1c1e-6a52-4c3e-9a51-2f1d8d7c4a10 holds BIF, not KES"),
          Map.entry(
              ErrorCode.ACCOUNT_FROZEN, "Account 0b6f1c1e-6a52-4c3e-9a51-2f1d8d7c4a10 is frozen"),
          Map.entry(ErrorCode.SAME_ACCOUNT_TRANSFER, "Source and destination accounts must differ"),
          Map.entry(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred."));

  @Override
  public Operation customize(Operation operation, HandlerMethod handlerMethod) {
    for (ErrorCode code : documentedErrors(operation, handlerMethod)) {
      addExample(operation, code);
    }
    return operation;
  }

  private static Set<ErrorCode> documentedErrors(Operation operation, HandlerMethod method) {
    Set<ErrorCode> codes = EnumSet.noneOf(ErrorCode.class);
    if (method.getMethodParameters().length > 0) {
      codes.add(ErrorCode.VALIDATION_FAILED);
    }
    if (!isPublic(operation)) {
      codes.add(ErrorCode.UNAUTHENTICATED);
    }
    ApiErrors declared = method.getMethodAnnotation(ApiErrors.class);
    if (declared != null) {
      codes.addAll(List.of(declared.value()));
    }
    return codes;
  }

  /** An empty {@code @SecurityRequirements} (login) overrides the global bearer requirement. */
  private static boolean isPublic(Operation operation) {
    return operation.getSecurity() != null && operation.getSecurity().isEmpty();
  }

  private static void addExample(Operation operation, ErrorCode code) {
    ApiResponse response =
        operation
            .getResponses()
            .computeIfAbsent(
                String.valueOf(code.status().value()),
                status -> new ApiResponse().description(code.status().getReasonPhrase()));
    if (response.getContent() == null) {
      response.setContent(new Content());
    }
    MediaType media =
        response
            .getContent()
            .computeIfAbsent(PROBLEM_JSON, type -> new MediaType().schema(problemSchema()));
    media.addExamples(code.name(), new Example().summary(code.title()).value(body(code)));
  }

  private static Map<String, Object> body(ErrorCode code) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", "about:blank");
    body.put("title", code.title());
    body.put("status", code.status().value());
    body.put("detail", SAMPLE_DETAILS.get(code));
    body.put("code", code.name());
    return body;
  }

  /** RFC 7807 fields plus the API's stable {@code code}. */
  private static ObjectSchema problemSchema() {
    ObjectSchema schema = new ObjectSchema();
    schema.addProperty("type", new StringSchema());
    schema.addProperty("title", new StringSchema());
    schema.addProperty("status", new IntegerSchema());
    schema.addProperty("detail", new StringSchema());
    schema.addProperty("instance", new StringSchema());
    schema.addProperty(
        "code", new StringSchema().description("Stable machine-readable error code"));
    return schema;
  }
}
