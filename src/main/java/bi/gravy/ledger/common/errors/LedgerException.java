package bi.gravy.ledger.common.errors;

import java.util.UUID;

/** A business-rule failure that maps to a client-facing {@link ErrorCode}. */
public class LedgerException extends RuntimeException {

  private final ErrorCode code;

  public LedgerException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public static LedgerException accountNotFound(UUID accountId) {
    return new LedgerException(
        ErrorCode.ACCOUNT_NOT_FOUND, "Account %s not found".formatted(accountId));
  }

  public static LedgerException validation(String message) {
    return new LedgerException(ErrorCode.VALIDATION_FAILED, message);
  }

  public ErrorCode code() {
    return code;
  }
}
