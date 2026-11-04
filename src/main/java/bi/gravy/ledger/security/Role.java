package bi.gravy.ledger.security;

public enum Role {
  /** A rider or driver: may only see and move money from accounts they own. */
  USER,
  /** Back-office staff: may access every account, reverse transactions and reconcile. */
  ADMIN
}
