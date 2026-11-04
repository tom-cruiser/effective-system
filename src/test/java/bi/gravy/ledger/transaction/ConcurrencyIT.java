package bi.gravy.ledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import bi.gravy.ledger.reconciliation.ReconciliationService;
import bi.gravy.ledger.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Proves the locking strategy (ADR 0002) under real contention: many threads, one PostgreSQL. Each
 * request goes through the full HTTP stack (MockMvc), service, engine and database.
 */
class ConcurrencyIT extends IntegrationTest {

  private static final int THREADS = 32;

  @Autowired private ReconciliationService reconciliation;
  @Autowired private DataSource dataSource;

  /**
   * The classic double-spend: 100 transfers of 1 race for a balance of 50. Without the row lock,
   * several threads could read "50", all pass the funds check, and overdraw the wallet.
   */
  @Test
  void hundredConcurrentTransfersCannotSpendMoreThanTheBalance() throws Exception {
    UUID payer = createFundedWallet("USER_WALLET", "BIF", 50);
    UUID payee = createWallet("USER_WALLET", "BIF");

    List<MockHttpServletResponse> responses =
        runConcurrently(100, i -> () -> transfer(payer, payee, 1, "BIF").andReturn().getResponse());

    Map<String, Long> outcomes =
        responses.stream()
            .collect(Collectors.groupingBy(ConcurrencyIT::outcome, Collectors.counting()));
    assertThat(outcomes)
        .containsExactlyInAnyOrderEntriesOf(Map.of("201", 50L, "INSUFFICIENT_FUNDS", 50L));
    assertThat(cachedBalance(payer)).isZero().isEqualTo(derivedBalance(payer));
    assertThat(cachedBalance(payee)).isEqualTo(50).isEqualTo(derivedBalance(payee));
    assertThat(lowestBalanceEver(payer)).isZero();
    assertThat(reconciliation.reconcile().clean()).isTrue();
  }

  /**
   * A→B and B→A at the same time is the textbook deadlock if each transfer locks "its" source
   * first. Locking in ascending id order means every one of these must succeed.
   */
  @Test
  void opposingTransfersBetweenTheSameWalletsNeverDeadlock() throws Exception {
    UUID a = createFundedWallet("USER_WALLET", "RWF", 1_000);
    UUID b = createFundedWallet("USER_WALLET", "RWF", 1_000);

    List<MockHttpServletResponse> responses =
        runConcurrently(
            200,
            i ->
                () ->
                    (i % 2 == 0 ? transfer(a, b, 1, "RWF") : transfer(b, a, 1, "RWF"))
                        .andReturn()
                        .getResponse());

    assertThat(responses).extracting(ConcurrencyIT::outcome).containsOnly("201");
    assertThat(cachedBalance(a)).isEqualTo(1_000).isEqualTo(derivedBalance(a));
    assertThat(cachedBalance(b)).isEqualTo(1_000).isEqualTo(derivedBalance(b));
    assertThat(reconciliation.reconcile().clean()).isTrue();
  }

  /**
   * Negative control for the test above: two raw transactions that lock the same two rows in
   * opposite order really do deadlock, and PostgreSQL aborts one of them. This is the failure the
   * lock-ordering rule exists to prevent.
   */
  @Test
  void lockingInOppositeOrderDoesDeadlock() throws Exception {
    UUID a = createWallet("USER_WALLET", "BIF");
    UUID b = createWallet("USER_WALLET", "BIF");
    CyclicBarrier bothHoldFirstLock = new CyclicBarrier(2);

    try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
      Future<?> first = pool.submit(() -> lockBoth(a, b, bothHoldFirstLock));
      Future<?> second = pool.submit(() -> lockBoth(b, a, bothHoldFirstLock));

      List<Throwable> failures = new ArrayList<>();
      for (Future<?> f : List.of(first, second)) {
        try {
          f.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
          failures.add(e.getCause());
        }
      }
      // Exactly one victim, aborted with SQLSTATE 40P01 (deadlock_detected).
      assertThat(failures)
          .singleElement()
          .isInstanceOfSatisfying(
              SQLException.class, e -> assertThat(e.getSQLState()).isEqualTo("40P01"));
    }
  }

  /** Every kind of money movement at once, across shared wallets; the books must still balance. */
  @Test
  void mixedWorkloadLeavesTheLedgerReconciled() throws Exception {
    UUID rider1 = createFundedWallet("USER_WALLET", "KES", 100_000);
    UUID rider2 = createFundedWallet("USER_WALLET", "KES", 100_000);
    UUID driver = createFundedWallet("DRIVER_WALLET", "KES", 10_000);

    List<MockHttpServletResponse> responses =
        runConcurrently(
            120,
            i ->
                () ->
                    (switch (i % 4) {
                          case 0 -> topUp(i % 8 == 0 ? rider1 : rider2, 500, "KES");
                          case 1 -> transfer(rider1, rider2, 300, "KES");
                          case 2 -> ridePayment(rider2, driver, 1_000, "KES");
                          default -> payout(driver, 200, "KES");
                        })
                        .andReturn()
                        .getResponse());

    assertThat(responses).extracting(ConcurrencyIT::outcome).doesNotContain("500");
    for (UUID wallet : List.of(rider1, rider2, driver)) {
      assertThat(cachedBalance(wallet)).isEqualTo(derivedBalance(wallet));
      assertThat(lowestBalanceEver(wallet)).isNotNegative();
    }
    assertThat(reconciliation.reconcile().clean()).isTrue();
  }

  // --- helpers -------------------------------------------------------------------------------

  /**
   * Submits {@code count} tasks that all wait on one latch, so they start as close together as
   * possible.
   */
  private static <T> List<T> runConcurrently(int count, Function<Integer, Callable<T>> task)
      throws Exception {
    CountDownLatch start = new CountDownLatch(1);
    List<Future<T>> futures = new ArrayList<>();
    try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
      for (int i = 0; i < count; i++) {
        Callable<T> work = task.apply(i);
        futures.add(
            pool.submit(() -> {
              start.await();
              return work.call();
            }));
      }
      start.countDown();
      List<T> results = new ArrayList<>();
      for (Future<T> future : futures) {
        results.add(future.get(60, TimeUnit.SECONDS));
      }
      return results;
    }
  }

  /** "201" on success, otherwise the error code (or the bare status if there is none). */
  private static String outcome(MockHttpServletResponse response) {
    if (response.getStatus() == 201) {
      return "201";
    }
    try {
      return JsonPath.read(response.getContentAsString(), "$.code");
    } catch (Exception e) {
      return String.valueOf(response.getStatus());
    }
  }

  private Void lockBoth(UUID first, UUID second, CyclicBarrier barrier) throws Exception {
    try (Connection connection = dataSource.getConnection()) {
      connection.setAutoCommit(false);
      try {
        lockRow(connection, first);
        barrier.await(10, TimeUnit.SECONDS);
        lockRow(connection, second);
        return null;
      } finally {
        connection.rollback();
      }
    }
  }

  private static void lockRow(Connection connection, UUID accountId) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT id FROM accounts WHERE id = ? FOR UPDATE")) {
      statement.setObject(1, accountId);
      statement.executeQuery().close();
    }
  }
}
