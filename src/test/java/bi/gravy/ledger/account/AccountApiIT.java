package bi.gravy.ledger.account;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bi.gravy.ledger.security.Role;
import bi.gravy.ledger.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class AccountApiIT extends IntegrationTest {

  @Test
  void createsAWalletWithZeroBalance() throws Exception {
    UUID owner = UUID.randomUUID();

    String json =
        """
        {"ownerId": "%s", "name": "Alice", "type": "USER_WALLET", "currency": "BIF"}
        """
            .formatted(owner);
    var result =
        postJson("/api/v1/accounts", null, json)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ownerId").value(owner.toString()))
            .andExpect(jsonPath("$.type").value("USER_WALLET"))
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.balance.amountMinor").value(0))
            .andExpect(jsonPath("$.balance.amount").value("0"))
            .andReturn();

    String location = result.getResponse().getHeader("Location");
    mvc.perform(get(location))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.name").value("Alice"));
  }

  @Test
  void formatsTheBalanceWithTheCurrencysDecimals() throws Exception {
    UUID wallet = createFundedWallet("DRIVER_WALLET", "USD", 1_234);

    mvc.perform(get("/api/v1/accounts/{id}", wallet))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.balance.amountMinor").value(1_234))
        .andExpect(jsonPath("$.balance.amount").value("12.34"));
  }

  @Test
  void returnsTheLocationOfTheNewAccount() throws Exception {
    postJson(
            "/api/v1/accounts",
            null,
            """
            {"ownerId": "%s", "name": "Bob", "type": "DRIVER_WALLET", "currency": "RWF"}
            """
                .formatted(UUID.randomUUID()))
        .andExpect(header().string("Location", startsWith("/api/v1/accounts/")));
  }

  @Test
  void systemAccountsCannotBeCreatedThroughTheApi() throws Exception {
    postJson(
            "/api/v1/accounts",
            null,
            """
            {"ownerId": "%s", "name": "x", "type": "SYSTEM", "currency": "BIF"}
            """
                .formatted(UUID.randomUUID()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void invalidBodyListsEveryFieldError() throws Exception {
    postJson(
            "/api/v1/accounts",
            null,
            """
            {"name": " ", "type": null, "currency": "EUR"}
            """)
        .andExpect(status().isBadRequest())
        .andExpect(header().string("Content-Type", MediaType.APPLICATION_PROBLEM_JSON_VALUE))
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("type", "name", "currency")));
  }

  @Test
  void unknownEnumValueIsAValidationError() throws Exception {
    postJson(
            "/api/v1/accounts",
            null,
            """
            {"ownerId": "%s", "name": "x", "type": "SAVINGS", "currency": "BIF"}
            """
                .formatted(UUID.randomUUID()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void unknownAccountIs404() throws Exception {
    UUID id = UUID.randomUUID();

    mvc.perform(get("/api/v1/accounts/{id}", id))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"))
        .andExpect(jsonPath("$.detail", endsWith("not found")));
  }

  @Test
  void malformedIdIsAValidationError() throws Exception {
    mvc.perform(get("/api/v1/accounts/not-a-uuid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void listsOnlyTheCallersOwnWalletsOldestFirst() throws Exception {
    UUID rider = UUID.randomUUID();
    String token = bearer(rider, Role.USER);
    for (String name : new String[] {"first", "second"}) {
      mvc.perform(
              post("/api/v1/accounts")
                  .header("Authorization", token)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                      {"name": "%s", "type": "USER_WALLET", "currency": "KES"}
                      """
                          .formatted(name)))
          .andExpect(status().isCreated());
    }
    createWallet("USER_WALLET", "KES"); // someone else's

    mvc.perform(get("/api/v1/accounts").header("Authorization", token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[*].name", contains("first", "second")))
        .andExpect(jsonPath("$[*].ownerId", contains(rider.toString(), rider.toString())));
  }
}
