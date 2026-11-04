package bi.gravy.ledger.security;

import bi.gravy.ledger.common.errors.ErrorCode;
import bi.gravy.ledger.common.errors.LedgerException;
import java.util.Collection;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Ownership rules: a USER may only read or move money from accounts they own; an ADMIN may access
 * any account.
 *
 * <p>Called from controllers, i.e. before and outside the money-moving database transaction. It
 * reads only {@code owner_id} with plain SQL, so it never loads an {@code Account} entity that the
 * locked transaction could later see as a stale copy (ADR 0002). Ownership never changes, so
 * checking it before the transaction is safe.
 */
@Component
public class AccessGuard {

  private final JdbcClient jdbc;

  public AccessGuard(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  private record Owner(UUID id) {}

  /** The caller must own the account (or be an admin). Unknown accounts are 404. */
  public void requireAccountAccess(UUID accountId) {
    CurrentUser user = CurrentUser.get();
    if (user.admin()) {
      return; // the business flow reports unknown accounts itself
    }
    Owner owner =
        jdbc.sql("SELECT owner_id FROM accounts WHERE id = ?")
            .param(accountId)
            .query((rs, i) -> new Owner(rs.getObject("owner_id", UUID.class)))
            .optional()
            .orElseThrow(() -> LedgerException.accountNotFound(accountId));
    if (!user.id().equals(owner.id())) {
      throw accessDenied();
    }
  }

  /** The caller must own at least one of the accounts, e.g. to view a transaction. */
  public void requireAnyAccountAccess(Collection<UUID> accountIds) {
    CurrentUser user = CurrentUser.get();
    if (user.admin()) {
      return;
    }
    boolean ownsOne =
        !accountIds.isEmpty()
            && jdbc.sql(
                    "SELECT EXISTS (SELECT 1 FROM accounts WHERE id IN (:ids) AND owner_id = :owner)")
                .param("ids", accountIds)
                .param("owner", user.id())
                .query(Boolean.class)
                .single();
    if (!ownsOne) {
      throw accessDenied();
    }
  }

  /**
   * Who a new wallet belongs to: the caller by default. Only an admin may open a wallet for someone
   * else.
   */
  public UUID resolveOwner(UUID requestedOwner) {
    CurrentUser user = CurrentUser.get();
    if (requestedOwner == null) {
      return user.id();
    }
    if (!user.admin() && !requestedOwner.equals(user.id())) {
      throw accessDenied();
    }
    return requestedOwner;
  }

  private static LedgerException accessDenied() {
    return new LedgerException(
        ErrorCode.ACCESS_DENIED, "You are not allowed to access this account.");
  }
}
