package bi.gravy.ledger.account;

/** The platform-owned accounts seeded once per currency by Flyway (V2). */
public enum SystemAccount {
  /** Money arriving from mobile-money providers; debited on every top-up. */
  TOPUP_CLEARING,
  /** Money on its way out to drivers' mobile-money accounts; credited on every payout. */
  PAYOUT_CLEARING,
  /** The platform's commission on rides; credited on every ride payment. */
  PLATFORM_FEES
}
