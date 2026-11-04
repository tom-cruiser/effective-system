package bi.gravy.ledger.transaction;

import bi.gravy.ledger.account.Account;
import java.util.Map;
import java.util.UUID;

/**
 * Accounts that the current database transaction holds row locks on. {@link PostingEngine#post}
 * only accepts postings to accounts in here, so it is impossible to write a posting without having
 * locked its account first.
 */
public record LockedAccounts(Map<UUID, Account> byId) {

  public LockedAccounts {
    byId = Map.copyOf(byId);
  }

  public Account get(UUID accountId) {
    Account account = byId.get(accountId);
    if (account == null) {
      throw new IllegalStateException("Account %s was not locked".formatted(accountId));
    }
    return account;
  }
}
