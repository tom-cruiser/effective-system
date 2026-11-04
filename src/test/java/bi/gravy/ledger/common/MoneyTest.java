package bi.gravy.ledger.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bi.gravy.ledger.common.errors.ErrorCode;
import bi.gravy.ledger.common.errors.LedgerException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MoneyTest {

  @Test
  void rejectsUnsupportedOrMissingCurrency() {
    assertThatThrownBy(() -> Money.of(100, "EUR")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> Money.of(100, null)).isInstanceOf(NullPointerException.class);
  }

  @Test
  void addsAndSubtractsInTheSameCurrency() {
    Money a = Money.of(1_500, "KES");
    Money b = Money.of(400, "KES");

    assertThat(a.plus(b)).isEqualTo(Money.of(1_900, "KES"));
    assertThat(b.minus(a)).isEqualTo(Money.of(-1_100, "KES"));
    assertThat(b.minus(a).isNegative()).isTrue();
    assertThat(Money.zero("KES").isPositive()).isFalse();
  }

  @Test
  void refusesToMixCurrencies() {
    assertThatThrownBy(() -> Money.of(1, "BIF").plus(Money.of(1, "RWF")))
        .isInstanceOfSatisfying(
            LedgerException.class,
            e -> assertThat(e.code()).isEqualTo(ErrorCode.CURRENCY_MISMATCH));
    assertThatThrownBy(() -> Money.of(1, "USD").minus(Money.of(1, "KES")))
        .isInstanceOf(LedgerException.class);
  }

  @Test
  void failsLoudlyOnOverflowInsteadOfWrappingAround() {
    Money max = Money.of(Long.MAX_VALUE, "USD");

    assertThatThrownBy(() -> max.plus(Money.of(1, "USD"))).isInstanceOf(ArithmeticException.class);
  }

  @ParameterizedTest
  @CsvSource({
    "BIF, 0, 10000, 10000",
    "RWF, 0, 2500, 2500",
    "USD, 2, 1234, 12.34",
    "USD, 2, 5, 0.05",
    "USD, 2, -5, -0.05",
    "KES, 2, 100000, 1000.00"
  })
  void formatsUsingTheCurrencysMinorUnits(
      String currency, int digits, long amountMinor, String expected) {
    Money money = Money.of(amountMinor, currency);

    assertThat(money.fractionDigits()).isEqualTo(digits);
    assertThat(money.toDecimalString()).isEqualTo(expected);
  }
}
