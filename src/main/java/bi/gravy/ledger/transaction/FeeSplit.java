package bi.gravy.ledger.transaction;

import bi.gravy.ledger.common.Money;

/** How a ride fare is divided between the platform and the driver. */
public record FeeSplit(Money platformFee, Money driverShare) {

  /** 10,000 basis points = 100%. */
  public static final int BPS_PER_WHOLE = 10_000;

  /**
   * The fee is rounded <em>down</em> to a whole minor unit and the driver gets the remainder, so
   * the two parts always add up to the fare exactly and no money is created or lost. For a positive
   * fare, Java's integer division truncates, which is rounding down.
   *
   * <p>Example: a 10,001 BIF fare at 1500 bps gives a fee of 1,500 BIF (not 1,500.15, since BIF has
   * no minor unit) and the driver 8,501 BIF.
   */
  public static FeeSplit of(Money fare, int feeBps) {
    if (!fare.isPositive()) {
      throw new IllegalArgumentException("Fare must be positive");
    }
    if (feeBps < 0 || feeBps > BPS_PER_WHOLE) {
      throw new IllegalArgumentException("Fee must be between 0 and 10000 bps, got " + feeBps);
    }
    long feeMinor = Math.multiplyExact(fare.amountMinor(), feeBps) / BPS_PER_WHOLE;
    Money platformFee = Money.of(feeMinor, fare.currency());
    return new FeeSplit(platformFee, fare.minus(platformFee));
  }
}
