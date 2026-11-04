package bi.gravy.ledger.support;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bi.gravy.ledger.TestcontainersConfiguration;
import bi.gravy.ledger.security.Role;
import bi.gravy.ledger.security.TokenService;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Base class for tests that run the whole application against a real PostgreSQL.
 *
 * <p>Postings are immutable even for tests (the trigger forbids DELETE and TRUNCATE), so tests
 * never clean up: each one creates fresh wallets and only asserts on those.
 *
 * <p>{@link #mvc} sends every request as the seeded ADMIN unless the test sets its own {@code
 * Authorization} header, so business tests are not cluttered with auth. Security behaviour is
 * tested explicitly in {@code SecurityIT}, using {@link #anonymousMvc} and {@link #bearer}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class IntegrationTest {

  /** The seeded demo admin (V3__users.sql). */
  protected static final UUID ADMIN_ID = UUID.fromString("b7d4c1a0-5e3f-4c2a-9d1e-1a2b3c4d5e02");

  /** Spring's auto-configured MockMvc: security filters on, no credentials. */
  @Autowired protected MockMvc anonymousMvc;

  @Autowired protected JdbcTemplate jdbc;
  @Autowired protected TokenService tokenService;
  @Autowired private WebApplicationContext context;

  /** Same application, but every request defaults to the admin's bearer token. */
  protected MockMvc mvc;

  @BeforeEach
  void authenticateAsAdminByDefault() {
    mvc =
        MockMvcBuilders.webAppContextSetup(context)
            .apply(springSecurity())
            .defaultRequest(
                get("/").header(HttpHeaders.AUTHORIZATION, bearer(ADMIN_ID, Role.ADMIN)))
            .build();
  }

  /** An {@code Authorization} header value for any user id, signed with the app's real key. */
  protected String bearer(UUID userId, Role role) {
    return "Bearer " + tokenService.issue(userId, userId + "@test.local", role).value();
  }

  protected UUID createWallet(String type, String currency) throws Exception {
    String body =
        """
        {"ownerId": "%s", "name": "test wallet", "type": "%s", "currency": "%s"}
        """
            .formatted(UUID.randomUUID(), type, currency);
    String response =
        mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return UUID.fromString(JsonPath.read(response, "$.id"));
  }

  protected UUID createFundedWallet(String type, String currency, long amountMinor)
      throws Exception {
    UUID wallet = createWallet(type, currency);
    topUp(wallet, amountMinor, currency).andExpect(status().isCreated());
    return wallet;
  }

  protected ResultActions topUp(UUID accountId, long amountMinor, String currency)
      throws Exception {
    return postJson(
        "/api/v1/transactions/top-ups",
        newKey(),
        """
        {"accountId": "%s", "amountMinor": %d, "currency": "%s", "externalReference": "MM-%s"}
        """
            .formatted(accountId, amountMinor, currency, UUID.randomUUID()));
  }

  protected ResultActions transfer(UUID from, UUID to, long amountMinor, String currency)
      throws Exception {
    return postJson(
        "/api/v1/transactions/transfers",
        newKey(),
        """
        {"fromAccountId": "%s", "toAccountId": "%s", "amountMinor": %d, "currency": "%s"}
        """
            .formatted(from, to, amountMinor, currency));
  }

  protected ResultActions ridePayment(UUID rider, UUID driver, long fareMinor, String currency)
      throws Exception {
    return postJson(
        "/api/v1/transactions/ride-payments",
        newKey(),
        """
        {"riderAccountId": "%s", "driverAccountId": "%s", "fareMinor": %d, "currency": "%s",
         "rideId": "ride-%s"}
        """
            .formatted(rider, driver, fareMinor, currency, UUID.randomUUID()));
  }

  protected ResultActions payout(UUID driver, long amountMinor, String currency) throws Exception {
    return postJson(
        "/api/v1/transactions/payouts",
        newKey(),
        """
        {"driverAccountId": "%s", "amountMinor": %d, "currency": "%s"}
        """
            .formatted(driver, amountMinor, currency));
  }

  protected ResultActions reverse(String transactionId) throws Exception {
    return postJson(
        "/api/v1/transactions/" + transactionId + "/reversal",
        newKey(),
        """
        {"reason": "test"}
        """);
  }

  /** The {@code id} field of a JSON response. */
  protected static String idOf(ResultActions result) throws Exception {
    return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
  }

  protected ResultActions postJson(String path, String idempotencyKey, String json)
      throws Exception {
    var request = post(path).contentType(MediaType.APPLICATION_JSON).content(json);
    if (idempotencyKey != null) {
      request.header("Idempotency-Key", idempotencyKey);
    }
    return mvc.perform(request);
  }

  protected static String newKey() {
    return UUID.randomUUID().toString();
  }

  protected long cachedBalance(UUID accountId) {
    return jdbc.queryForObject(
        "SELECT balance_minor FROM accounts WHERE id = ?", Long.class, accountId);
  }

  /** The balance recomputed from postings, independently of the cached column (invariant 8). */
  protected long derivedBalance(UUID accountId) {
    return jdbc.queryForObject(
        """
        SELECT COALESCE(SUM(CASE direction WHEN 'CREDIT' THEN amount_minor ELSE -amount_minor END), 0)
        FROM postings WHERE account_id = ?
        """,
        Long.class,
        accountId);
  }

  protected long postingCount(UUID accountId) {
    return jdbc.queryForObject(
        "SELECT count(*) FROM postings WHERE account_id = ?", Long.class, accountId);
  }

  protected UUID systemAccountId(String name, String currency) {
    return jdbc.queryForObject(
        "SELECT id FROM accounts WHERE type = 'SYSTEM' AND name = ? AND currency = ?",
        UUID.class,
        name,
        currency);
  }

  /** Lowest balance the account ever had, according to its postings' running balances. */
  protected long lowestBalanceEver(UUID accountId) {
    return jdbc.queryForObject(
        "SELECT COALESCE(MIN(balance_after_minor), 0) FROM postings WHERE account_id = ?",
        Long.class,
        accountId);
  }

  protected void freeze(UUID accountId) {
    jdbc.update("UPDATE accounts SET status = 'FROZEN' WHERE id = ?", accountId);
  }
}
