package bi.gravy.ledger.reconciliation;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Result of checking the ledger against itself. {@code clean} is true only when every list of
 * problems is empty.
 */
public record ReconciliationReport(
    boolean clean,
    Instant checkedAt,
    List<CurrencyTotals> currencies,
    List<BalanceMismatch> balanceMismatches,
    List<UUID> unbalancedTransactions) {

  public ReconciliationReport {
    currencies = List.copyOf(currencies);
    balanceMismatches = List.copyOf(balanceMismatches);
    unbalancedTransactions = List.copyOf(unbalancedTransactions);
  }

  /** Sum of all debits and all credits ever posted in one currency; they must be equal. */
  public record CurrencyTotals(String currency, long debitsMinor, long creditsMinor) {
    public boolean isBalanced() {
      return debitsMinor == creditsMinor;
    }
  }

  /** An account whose cached balance disagrees with the sum of its postings. */
  public record BalanceMismatch(UUID accountId, long cachedMinor, long derivedMinor) {}
}
