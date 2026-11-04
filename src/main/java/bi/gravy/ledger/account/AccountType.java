package bi.gravy.ledger.account;

public enum AccountType {
  /** A rider's wallet. A liability of the platform; never negative. */
  USER_WALLET,
  /** A driver's earnings wallet. A liability of the platform; never negative. */
  DRIVER_WALLET,
  /** A platform-owned account (clearing or revenue). May go negative. */
  SYSTEM;

  public boolean isWallet() {
    return this != SYSTEM;
  }
}
