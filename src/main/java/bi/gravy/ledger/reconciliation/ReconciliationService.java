package bi.gravy.ledger.reconciliation;

import bi.gravy.ledger.reconciliation.ReconciliationReport.BalanceMismatch;
import bi.gravy.ledger.reconciliation.ReconciliationReport.CurrencyTotals;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Re-derives the ledger's totals from the postings and compares them with what the application
 * believes. Plain SQL aggregates rather than JPA: this must not load millions of entities, and it
 * should be independent of the code it is checking.
 */
@Service
public class ReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

  private final JdbcClient jdbc;

  public ReconciliationService(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  /**
   * REPEATABLE READ gives all three queries the same snapshot. Under the default READ COMMITTED
   * each query would see a different moment, and a transfer committing between two of them could be
   * reported as a false mismatch.
   */
  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public ReconciliationReport reconcile() {
    List<CurrencyTotals> currencies = currencyTotals();
    List<BalanceMismatch> mismatches = balanceMismatches();
    List<UUID> unbalanced = unbalancedTransactions();

    boolean clean =
        currencies.stream().allMatch(CurrencyTotals::isBalanced)
            && mismatches.isEmpty()
            && unbalanced.isEmpty();
    if (!clean) {
      log.error(
          "Reconciliation failed: {} balance mismatches, {} unbalanced transactions",
          mismatches.size(),
          unbalanced.size());
    }
    return new ReconciliationReport(clean, Instant.now(), currencies, mismatches, unbalanced);
  }

  /** (a) Globally, per currency, total debits must equal total credits. */
  private List<CurrencyTotals> currencyTotals() {
    return jdbc.sql(
            """
            SELECT currency,
                   COALESCE(SUM(amount_minor) FILTER (WHERE direction = 'DEBIT'), 0)  AS debits,
                   COALESCE(SUM(amount_minor) FILTER (WHERE direction = 'CREDIT'), 0) AS credits
            FROM postings
            GROUP BY currency
            ORDER BY currency
            """)
        .query((rs, i) ->
            new CurrencyTotals(
                rs.getString("currency"), rs.getLong("debits"), rs.getLong("credits")))
        .list();
  }

  /** (b) Every cached balance must equal the balance derived from that account's postings. */
  private List<BalanceMismatch> balanceMismatches() {
    return jdbc.sql(
            """
            SELECT a.id, a.balance_minor AS cached,
                   COALESCE(SUM(CASE p.direction WHEN 'CREDIT' THEN p.amount_minor
                                                 ELSE -p.amount_minor END), 0) AS derived
            FROM accounts a
            LEFT JOIN postings p ON p.account_id = a.id
            GROUP BY a.id, a.balance_minor
            HAVING a.balance_minor <> COALESCE(SUM(CASE p.direction WHEN 'CREDIT' THEN p.amount_minor
                                                                   ELSE -p.amount_minor END), 0)
            """)
        .query((rs, i) ->
            new BalanceMismatch(
                rs.getObject("id", UUID.class), rs.getLong("cached"), rs.getLong("derived")))
        .list();
  }

  /**
   * (c) A finer version of (a): each individual transaction must balance and have at least two
   * postings. Global totals could hide two errors that cancel out; this cannot.
   */
  private List<UUID> unbalancedTransactions() {
    return jdbc.sql(
            """
            SELECT transaction_id
            FROM postings
            GROUP BY transaction_id
            HAVING COUNT(*) < 2
                OR SUM(CASE direction WHEN 'CREDIT' THEN amount_minor ELSE -amount_minor END) <> 0
            """)
        .query((rs, i) -> rs.getObject("transaction_id", UUID.class))
        .list();
  }
}
