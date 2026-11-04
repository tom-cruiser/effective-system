package bi.gravy.ledger.account;

public enum AccountStatus {
  ACTIVE,
  /** No postings in either direction until unfrozen (e.g. during a fraud investigation). */
  FROZEN
}
