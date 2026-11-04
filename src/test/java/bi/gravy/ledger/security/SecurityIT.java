package bi.gravy.ledger.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bi.gravy.ledger.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** Authentication (401), role rules (403) and ownership rules (403). */
class SecurityIT extends IntegrationTest {

  private static final String RIDER_ID = "b7d4c1a0-5e3f-4c2a-9d1e-1a2b3c4d5e01";

  // --- login ---------------------------------------------------------------------------------

  @Test
  void seededRiderCanLogInAndUseTheToken() throws Exception {
    String response =
        login("rider@demo.local", "rider-demo-123")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(3600))
            .andReturn()
            .getResponse()
            .getContentAsString();
    String bearer = "Bearer " + JsonPath.read(response, "$.accessToken");

    // A wallet created without an ownerId belongs to the caller.
    anonymousMvc
        .perform(
            post("/api/v1/accounts")
                .header(HttpHeaders.AUTHORIZATION, bearer)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"name": "My wallet", "type": "USER_WALLET", "currency": "BIF"}
                    """))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.ownerId").value(RIDER_ID));
  }

  @Test
  void wrongPasswordAndUnknownEmailGetTheSameAnswer() throws Exception {
    for (ResultActions attempt :
        List.of(
            login("rider@demo.local", "wrong-password"),
            login("nobody@demo.local", "rider-demo-123"))) {
      attempt
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
          .andExpect(jsonPath("$.detail").value("Email or password is incorrect"));
    }
  }

  @Test
  void loginBodyIsValidated() throws Exception {
    login("", "")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  // --- 401: who are you? ---------------------------------------------------------------------

  @Test
  void requestsWithoutATokenAreRejected() throws Exception {
    anonymousMvc
        .perform(get("/api/v1/accounts/{id}", UUID.randomUUID()))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
        .andExpect(
            header().string(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
  }

  @Test
  void malformedExpiredOrForeignTokensAreRejected() throws Exception {
    UUID user = UUID.randomUUID();
    Instant twoHoursAgo = Instant.now().minus(Duration.ofHours(2));
    String expired = tokenService.issue(user, "x@test.local", Role.USER, twoHoursAgo).value();

    for (String token : List.of("not-a-jwt", expired, tokenSignedWithAnotherKey(user))) {
      anonymousMvc
          .perform(
              get("/api/v1/accounts/{id}", UUID.randomUUID())
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
  }

  @Test
  void healthAndApiDocsArePublic() throws Exception {
    anonymousMvc.perform(get("/actuator/health")).andExpect(status().isOk());
    anonymousMvc.perform(get("/v3/api-docs")).andExpect(status().isOk());
  }

  // --- 403: role rules -----------------------------------------------------------------------

  @Test
  void usersCannotReconcileOrReverse() throws Exception {
    UUID user = UUID.randomUUID();

    asUser(user, get("/api/v1/admin/reconciliation"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    asUser(user, post("/api/v1/transactions/{id}/reversal", UUID.randomUUID()))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
  }

  @Test
  void adminsCanReconcile() throws Exception {
    mvc.perform(get("/api/v1/admin/reconciliation")).andExpect(status().isOk());
  }

  // --- 403: ownership rules ------------------------------------------------------------------

  @Test
  void usersSeeOnlyTheirOwnAccountsAndStatements() throws Exception {
    UUID alice = UUID.randomUUID();
    UUID aliceWallet = walletOwnedBy(alice, "USER_WALLET", 0);
    UUID bobWallet = walletOwnedBy(UUID.randomUUID(), "USER_WALLET", 0);

    asUser(alice, get("/api/v1/accounts/{id}", aliceWallet)).andExpect(status().isOk());
    asUser(alice, get("/api/v1/accounts/{id}/statement", aliceWallet)).andExpect(status().isOk());
    asUser(alice, get("/api/v1/accounts/{id}", bobWallet))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    asUser(alice, get("/api/v1/accounts/{id}/statement", bobWallet))
        .andExpect(status().isForbidden());
    asUser(alice, get("/api/v1/accounts/{id}", UUID.randomUUID())).andExpect(status().isNotFound());
  }

  @Test
  void usersCanOnlyMoveMoneyOutOfTheirOwnWallets() throws Exception {
    UUID alice = UUID.randomUUID();
    UUID aliceWallet = walletOwnedBy(alice, "USER_WALLET", 1_000);
    UUID bobWallet = walletOwnedBy(UUID.randomUUID(), "USER_WALLET", 1_000);

    asUser(alice, transferRequest(bobWallet, aliceWallet, 500))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    assertThat(cachedBalance(bobWallet)).isEqualTo(1_000);

    asUser(alice, transferRequest(aliceWallet, bobWallet, 500)).andExpect(status().isCreated());
    assertThat(cachedBalance(aliceWallet)).isEqualTo(500);
  }

  @Test
  void ridersPayOnlyFromTheirOwnWalletAndDriversCashOutOnlyTheirOwn() throws Exception {
    UUID rider = UUID.randomUUID();
    UUID driver = UUID.randomUUID();
    UUID riderWallet = walletOwnedBy(rider, "USER_WALLET", 10_000);
    UUID driverWallet = walletOwnedBy(driver, "DRIVER_WALLET", 0);
    String ride =
        """
        {"riderAccountId": "%s", "driverAccountId": "%s", "fareMinor": 1000, "currency": "BIF",
         "rideId": "r1"}
        """
            .formatted(riderWallet, driverWallet);
    String payout =
        """
        {"driverAccountId": "%s", "amountMinor": 100, "currency": "BIF"}
        """
            .formatted(driverWallet);

    asUser(driver, post("/api/v1/transactions/ride-payments").content(ride))
        .andExpect(status().isForbidden());
    asUser(rider, post("/api/v1/transactions/ride-payments").content(ride))
        .andExpect(status().isCreated());
    asUser(rider, post("/api/v1/transactions/payouts").content(payout))
        .andExpect(status().isForbidden());
    asUser(driver, post("/api/v1/transactions/payouts").content(payout))
        .andExpect(status().isCreated());
  }

  @Test
  void usersSeeOnlyTransactionsTheyTakePartIn() throws Exception {
    UUID alice = UUID.randomUUID();
    UUID aliceWallet = walletOwnedBy(alice, "USER_WALLET", 1_000);
    UUID bobWallet = walletOwnedBy(UUID.randomUUID(), "USER_WALLET", 1_000);
    UUID carolWallet = walletOwnedBy(UUID.randomUUID(), "USER_WALLET", 0);
    String involving = idOf(transfer(aliceWallet, bobWallet, 1, "BIF"));
    String notInvolving = idOf(transfer(bobWallet, carolWallet, 1, "BIF"));

    asUser(alice, get("/api/v1/transactions/{id}", involving)).andExpect(status().isOk());
    asUser(alice, get("/api/v1/transactions/{id}", notInvolving)).andExpect(status().isForbidden());
  }

  @Test
  void onlyAdminsOpenWalletsForSomeoneElse() throws Exception {
    UUID alice = UUID.randomUUID();
    String template =
        """
        {"ownerId": "%s", "name": "x", "type": "USER_WALLET", "currency": "BIF"}
        """;

    asUser(alice, post("/api/v1/accounts").content(template.formatted(UUID.randomUUID())))
        .andExpect(status().isForbidden());
    asUser(alice, post("/api/v1/accounts").content(template.formatted(alice)))
        .andExpect(status().isCreated());
  }

  // --- helpers -------------------------------------------------------------------------------

  private ResultActions login(String email, String password) throws Exception {
    return anonymousMvc.perform(
        post("/api/v1/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(
                """
                {"email": "%s", "password": "%s"}
                """
                    .formatted(email, password)));
  }

  /** Performs the request as a USER with this id, with a JSON body and a fresh idempotency key. */
  private ResultActions asUser(UUID userId, MockHttpServletRequestBuilder request)
      throws Exception {
    return anonymousMvc.perform(
        request
            .header(HttpHeaders.AUTHORIZATION, bearer(userId, Role.USER))
            .header("Idempotency-Key", newKey())
            .contentType(MediaType.APPLICATION_JSON));
  }

  private static MockHttpServletRequestBuilder transferRequest(UUID from, UUID to, long amount) {
    return post("/api/v1/transactions/transfers")
        .content(
            """
            {"fromAccountId": "%s", "toAccountId": "%s", "amountMinor": %d, "currency": "BIF"}
            """
                .formatted(from, to, amount));
  }

  /** Opened and funded by the admin on the owner's behalf. */
  private UUID walletOwnedBy(UUID owner, String type, long balance) throws Exception {
    String body =
        postJson(
                "/api/v1/accounts",
                null,
                """
                {"ownerId": "%s", "name": "w", "type": "%s", "currency": "BIF"}
                """
                    .formatted(owner, type))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    UUID wallet = UUID.fromString(JsonPath.read(body, "$.id"));
    if (balance > 0) {
      topUp(wallet, balance, "BIF").andExpect(status().isCreated());
    }
    return wallet;
  }

  /** Well-formed and unexpired, even claiming ADMIN, but signed with a key the server lacks. */
  private static String tokenSignedWithAnotherKey(UUID user) {
    var key =
        new SecretKeySpec(
            "an-attackers-secret-that-is-at-least-32-bytes".getBytes(StandardCharsets.UTF_8),
            "HmacSHA256");
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer("wallet-ledger-api")
            .subject(user.toString())
            .expiresAt(Instant.now().plusSeconds(600))
            .claim(TokenService.ROLES_CLAIM, List.of("ADMIN"))
            .build();
    return new NimbusJwtEncoder(new ImmutableSecret<>(key))
        .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
        .getTokenValue();
  }
}
