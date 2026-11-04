package bi.gravy.ledger.common.idempotency;

import bi.gravy.ledger.common.errors.LedgerException;

/** Validation of the {@code Idempotency-Key} request header (stored in a VARCHAR(100) column). */
public final class IdempotencyKeys {

  public static final String HEADER = "Idempotency-Key";
  public static final int MAX_LENGTH = 100;

  private IdempotencyKeys() {}

  public static String requireValid(String key) {
    if (key == null || key.isBlank() || key.length() > MAX_LENGTH) {
      throw LedgerException.validation(
          "%s header must be 1-%d characters".formatted(HEADER, MAX_LENGTH));
    }
    return key;
  }
}
