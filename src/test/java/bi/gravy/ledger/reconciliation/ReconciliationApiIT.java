package bi.gravy.ledger.reconciliation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bi.gravy.ledger.support.IntegrationTest;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

class ReconciliationApiIT extends IntegrationTest {

  @Autowired private ReconciliationService reconciliation;
  @Autowired private TransactionTemplate transactionTemplate;

  @Test
  void reportsCleanAfterEveryKindOfTransaction() throws Exception {
    UUID rider = createFundedWallet("USER_WALLET", "BIF", 30_000);
    UUID driver = createWallet("DRIVER_WALLET", "BIF");
    String ride = idOf(ridePayment(rider, driver, 10_000, "BIF"));
    ridePayment(rider, driver, 7_777, "BIF");
    transfer(rider, createWallet("USER_WALLET", "BIF"), 1_000, "BIF");
    payout(driver, 5_000, "BIF");
    reverse(ride);

    mvc.perform(get("/api/v1/admin/reconciliation"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.clean").value(true))
        .andExpect(jsonPath("$.balanceMismatches").isEmpty())
        .andExpect(jsonPath("$.unbalancedTransactions").isEmpty())
        .andExpect(jsonPath("$.currencies[?(@.currency == 'BIF')].balanced").value(true));
  }

  /**
   * Corruption is simulated inside a transaction that is rolled back afterwards, so the shared test
   * database stays clean for every other test.
   */
  @Test
  void detectsACachedBalanceThatDisagreesWithPostings() throws Exception {
    UUID wallet = createFundedWallet("USER_WALLET", "BIF", 100);

    ReconciliationReport report =
        inRolledBackTransaction(() -> {
          jdbc.update("UPDATE accounts SET balance_minor = 150 WHERE id = ?", wallet);
          return reconciliation.reconcile();
        });

    assertThat(report.clean()).isFalse();
    assertThat(report.balanceMismatches())
        .containsExactly(new ReconciliationReport.BalanceMismatch(wallet, 150, 100));
  }

  @Test
  void detectsAnUnbalancedTransaction() throws Exception {
    UUID wallet = createWallet("USER_WALLET", "USD");
    UUID txId = UUID.randomUUID();

    ReconciliationReport report =
        inRolledBackTransaction(() -> {
          // Bypass the PostingEngine to write a one-sided entry, as a buggy writer might.
          jdbc.update(
              """
              INSERT INTO ledger_transactions (id, type, idempotency_key, request_hash)
              VALUES (?, 'TOP_UP', ?, 'x')
              """,
              txId,
              txId.toString());
          jdbc.update(
              """
              INSERT INTO postings (id, transaction_id, account_id, direction, amount_minor,
                                    currency, balance_after_minor)
              VALUES (gen_random_uuid(), ?, ?, 'CREDIT', 999, 'USD', 999)
              """,
              txId,
              wallet);
          return reconciliation.reconcile();
        });

    assertThat(report.clean()).isFalse();
    assertThat(report.unbalancedTransactions()).containsExactly(txId);
    assertThat(report.currencies())
        .filteredOn(c -> c.currency().equals("USD"))
        .singleElement()
        .satisfies(c -> assertThat(c.isBalanced()).isFalse());
    assertThat(report.balanceMismatches()).extracting("accountId").contains(wallet);
  }

  private ReconciliationReport inRolledBackTransaction(Supplier<ReconciliationReport> work) {
    return transactionTemplate.execute(status -> {
      status.setRollbackOnly();
      return work.get();
    });
  }
}
