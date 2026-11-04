package bi.gravy.ledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bi.gravy.ledger.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class IdempotencyApiIT extends IntegrationTest {

  private static final String TRANSFERS = "/api/v1/transactions/transfers";

  @Test
  void sameKeyAndBodyReturnTheOriginalTransactionWithoutPostingTwice() throws Exception {
    UUID alice = createFundedWallet("USER_WALLET", "BIF", 1_000);
    UUID bob = createWallet("USER_WALLET", "BIF");
    String key = newKey();
    String body = transferBody(alice, bob, 300);

    String firstId = idOf(postJson(TRANSFERS, key, body).andExpect(status().isCreated()));

    postJson(TRANSFERS, key, body)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(firstId))
        .andExpect(jsonPath("$.postings.length()").value(2));
    assertThat(cachedBalance(alice)).isEqualTo(700);
    assertThat(cachedBalance(bob)).isEqualTo(300);
    assertThat(postingCount(bob)).isEqualTo(1);
  }

  @Test
  void bodyFormattingDoesNotMatterOnlyItsContent() throws Exception {
    UUID alice = createFundedWallet("USER_WALLET", "BIF", 1_000);
    UUID bob = createWallet("USER_WALLET", "BIF");
    String key = newKey();
    postJson(TRANSFERS, key, transferBody(alice, bob, 300)).andExpect(status().isCreated());

    String reordered =
        """
        {  "currency":"BIF", "amountMinor":300,
           "toAccountId":"%s", "fromAccountId":"%s" }
        """
            .formatted(bob, alice);
    postJson(TRANSFERS, key, reordered).andExpect(status().isOk());
  }

  @Test
  void sameKeyWithADifferentBodyIsAConflict() throws Exception {
    UUID alice = createFundedWallet("USER_WALLET", "BIF", 1_000);
    UUID bob = createWallet("USER_WALLET", "BIF");
    String key = newKey();
    postJson(TRANSFERS, key, transferBody(alice, bob, 300)).andExpect(status().isCreated());

    postJson(TRANSFERS, key, transferBody(alice, bob, 301))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    assertThat(cachedBalance(alice)).isEqualTo(700);
  }

  @Test
  void sameKeyOnADifferentEndpointIsAConflict() throws Exception {
    UUID alice = createFundedWallet("USER_WALLET", "BIF", 1_000);
    String key = newKey();
    postJson(TRANSFERS, key, transferBody(alice, createWallet("USER_WALLET", "BIF"), 1))
        .andExpect(status().isCreated());

    postJson(
            "/api/v1/transactions/top-ups",
            key,
            """
            {"accountId": "%s", "amountMinor": 1, "currency": "BIF", "externalReference": "x"}
            """
                .formatted(alice))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
  }

  /**
   * Ten copies of a request that spends the whole balance arrive at once. All but one lose the
   * race; each must still get the winner's transaction back, not INSUFFICIENT_FUNDS or a 500.
   */
  @Test
  void concurrentDuplicatesAllGetTheSameTransaction() throws Exception {
    UUID alice = createFundedWallet("USER_WALLET", "BIF", 500);
    UUID bob = createWallet("USER_WALLET", "BIF");
    String key = newKey();
    String body = transferBody(alice, bob, 500);
    int threads = 10;
    CountDownLatch start = new CountDownLatch(1);
    List<Future<MockHttpServletResponse>> futures = new ArrayList<>();

    try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
      for (int i = 0; i < threads; i++) {
        futures.add(
            pool.submit(() -> {
              start.await();
              return postJson(TRANSFERS, key, body).andReturn().getResponse();
            }));
      }
      start.countDown();

      List<Integer> statuses = new ArrayList<>();
      List<String> ids = new ArrayList<>();
      for (Future<MockHttpServletResponse> future : futures) {
        MockHttpServletResponse response = future.get();
        statuses.add(response.getStatus());
        ids.add(JsonPath.read(response.getContentAsString(), "$.id"));
      }

      assertThat(statuses).containsOnly(200, 201).containsOnlyOnce(201);
      assertThat(ids).containsOnly(ids.get(0));
    }
    assertThat(cachedBalance(alice)).isZero();
    assertThat(cachedBalance(bob)).isEqualTo(500);
    assertThat(postingCount(bob)).isEqualTo(1);
  }

  private static String transferBody(UUID from, UUID to, long amount) {
    return """
    {"fromAccountId": "%s", "toAccountId": "%s", "amountMinor": %d, "currency": "BIF"}
    """
        .formatted(from, to, amount);
  }
}
