package bi.gravy.ledger.transaction;

/**
 * The side of a journal line. This enum is the single place where the accounting convention lives.
 *
 * <p>Every account in this ledger is <em>credit-normal</em>: a CREDIT increases its balance and a
 * DEBIT decreases it. For wallets that is the textbook rule, because a wallet balance is money the
 * platform owes the rider or driver (a liability). Using the same rule for system accounts means
 * that, in each currency, the balances of all accounts always sum to zero; TOPUP_CLEARING's
 * negative balance is exactly the outside money that has entered the system.
 */
public enum Direction {
  DEBIT,
  CREDIT;

  /** How a posting of {@code amountMinor} in this direction changes an account's balance. */
  public long signedAmount(long amountMinor) {
    return this == CREDIT ? amountMinor : -amountMinor;
  }

  /** The direction that undoes this one; used to build reversals. */
  public Direction opposite() {
    return this == CREDIT ? DEBIT : CREDIT;
  }
}
