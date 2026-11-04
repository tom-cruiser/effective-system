package bi.gravy.ledger.common;

import bi.gravy.ledger.common.errors.ErrorCode;
import bi.gravy.ledger.common.errors.LedgerException;
import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;
import java.util.Set;

/**
 * An amount of money in minor units (cents for USD, whole francs for BIF) of a supported currency.
 *
 * <p>Amounts are {@code long}s, never {@code double}: binary floating point cannot represent 0.10
 * exactly, and a ledger must add up to the last unit. See ADR 0001.
 */
public record Money(long amountMinor, String currency) {

  /** For Bean Validation on request DTOs; annotation values must be compile-time constants. */
  public static final String CURRENCY_PATTERN = "BIF|RWF|KES|USD";

  public static final Set<String> SUPPORTED_CURRENCIES = Set.of("BIF", "RWF", "KES", "USD");

  public Money {
    Objects.requireNonNull(currency, "currency");
    if (!SUPPORTED_CURRENCIES.contains(currency)) {
      throw new IllegalArgumentException("Unsupported currency: " + currency);
    }
  }

  public static Money of(long amountMinor, String currency) {
    return new Money(amountMinor, currency);
  }

  public static Money zero(String currency) {
    return new Money(0, currency);
  }

  public Money plus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.addExact(amountMinor, other.amountMinor), currency);
  }

  public Money minus(Money other) {
    requireSameCurrency(other);
    return new Money(Math.subtractExact(amountMinor, other.amountMinor), currency);
  }

  public boolean isPositive() {
    return amountMinor > 0;
  }

  public boolean isNegative() {
    return amountMinor < 0;
  }

  /** ISO 4217 minor-unit digits: 2 for USD and KES, 0 for BIF and RWF. */
  public int fractionDigits() {
    return Currency.getInstance(currency).getDefaultFractionDigits();
  }

  /** Human-readable major-unit amount, e.g. 1234 USD -> "12.34" and 10000 BIF -> "10000". */
  public String toDecimalString() {
    return BigDecimal.valueOf(amountMinor, fractionDigits()).toPlainString();
  }

  private void requireSameCurrency(Money other) {
    if (!currency.equals(other.currency)) {
      throw new LedgerException(
          ErrorCode.CURRENCY_MISMATCH,
          "Cannot combine %s with %s".formatted(currency, other.currency));
    }
  }
}
