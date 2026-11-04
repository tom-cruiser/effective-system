package bi.gravy.ledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bi.gravy.ledger.common.Money;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FeeSplitTest {

  @ParameterizedTest(name = "{0} {1} at {2} bps -> fee {3}, driver {4}")
  @CsvSource({
    // the README's worked example
    "10000, BIF, 1500, 1500, 8500",
    // 1500.15 BIF: BIF has no minor unit, so the fee rounds down to 1500 and the driver keeps 8501
    "10001, BIF, 1500, 1500, 8501",
    // 149.85 -> 149
    "999, RWF, 1500, 149, 850",
    // a fare too small to carry any fee
    "6, BIF, 1500, 0, 6",
    "1, USD, 1500, 0, 1",
    // USD cents: $12.34 at 15% = 185.1 cents -> 185
    "1234, USD, 1500, 185, 1049",
    "5000, KES, 0, 0, 5000",
    "5000, KES, 10000, 5000, 0"
  })
  void roundsTheFeeDownAndGivesTheRemainderToTheDriver(
      long fare, String currency, int bps, long expectedFee, long expectedDriver) {
    FeeSplit split = FeeSplit.of(Money.of(fare, currency), bps);

    assertThat(split.platformFee()).isEqualTo(Money.of(expectedFee, currency));
    assertThat(split.driverShare()).isEqualTo(Money.of(expectedDriver, currency));
    assertThat(split.platformFee().plus(split.driverShare()).amountMinor()).isEqualTo(fare);
  }

  @Test
  void rejectsInvalidInput() {
    assertThatThrownBy(() -> FeeSplit.of(Money.of(0, "BIF"), 1500))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FeeSplit.of(Money.of(100, "BIF"), -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> FeeSplit.of(Money.of(100, "BIF"), 10_001))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void hugeFaresOverflowLoudlyRatherThanSilently() {
    assertThatThrownBy(() -> FeeSplit.of(Money.of(Long.MAX_VALUE / 2, "BIF"), 1500))
        .isInstanceOf(ArithmeticException.class);
  }
}
