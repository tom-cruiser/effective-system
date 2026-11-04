package bi.gravy.ledger.common.errors;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned in the {@code code} field of every error response.
 * Clients branch on these, never on the human-readable message, so they must not be renamed.
 */
public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Validation failed"),
  UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid credentials"),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access denied"),
  ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Account not found"),
  TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Transaction not found"),
  IDEMPOTENCY_CONFLICT(HttpStatus.CONFLICT, "Idempotency key reused with a different request"),
  ALREADY_REVERSED(HttpStatus.CONFLICT, "Transaction already reversed"),
  INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_ENTITY, "Insufficient funds"),
  CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_ENTITY, "Currency mismatch"),
  ACCOUNT_FROZEN(HttpStatus.UNPROCESSABLE_ENTITY, "Account frozen"),
  SAME_ACCOUNT_TRANSFER(HttpStatus.UNPROCESSABLE_ENTITY, "Cannot transfer to the same account"),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal error");

  private final HttpStatus status;
  private final String title;

  ErrorCode(HttpStatus status, String title) {
    this.status = status;
    this.title = title;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }
}
