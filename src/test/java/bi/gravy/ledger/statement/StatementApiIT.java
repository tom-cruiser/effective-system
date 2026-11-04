package bi.gravy.ledger.statement;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bi.gravy.ledger.support.IntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StatementApiIT extends IntegrationTest {

  @Test
  void listsPostingsNewestFirstWithRunningBalance() throws Exception {
    UUID rider = createWallet("USER_WALLET", "BIF");
    UUID driver = createWallet("DRIVER_WALLET", "BIF");
    topUp(rider, 20_000, "BIF"); //               balance 20,000
    ridePayment(rider, driver, 10_000, "BIF"); // balance 10,000
    transfer(rider, createWallet("USER_WALLET", "BIF"), 2_500, "BIF"); // 7,500

    mvc.perform(get("/api/v1/accounts/{id}/statement", rider))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.currentBalance.amountMinor").value(7_500))
        .andExpect(jsonPath("$.totalElements").value(3))
        .andExpect(jsonPath("$.entries[0].transactionType").value("TRANSFER"))
        .andExpect(jsonPath("$.entries[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.entries[0].amount.amountMinor").value(2_500))
        .andExpect(jsonPath("$.entries[0].balanceAfter.amountMinor").value(7_500))
        .andExpect(jsonPath("$.entries[1].transactionType").value("RIDE_PAYMENT"))
        .andExpect(jsonPath("$.entries[1].balanceAfter.amountMinor").value(10_000))
        .andExpect(jsonPath("$.entries[2].transactionType").value("TOP_UP"))
        .andExpect(jsonPath("$.entries[2].direction").value("CREDIT"))
        .andExpect(jsonPath("$.entries[2].balanceAfter.amount").value("20000"));
  }

  @Test
  void paginates() throws Exception {
    UUID wallet = createWallet("USER_WALLET", "USD");
    for (int amount = 1; amount <= 5; amount++) {
      topUp(wallet, amount, "USD");
    }

    mvc.perform(get("/api/v1/accounts/{id}/statement?page=1&size=2", wallet))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(2))
        .andExpect(jsonPath("$.totalElements").value(5))
        .andExpect(jsonPath("$.totalPages").value(3))
        .andExpect(jsonPath("$.entries.length()").value(2))
        .andExpect(jsonPath("$.entries[0].amount.amountMinor").value(3))
        .andExpect(jsonPath("$.entries[0].balanceAfter.amountMinor").value(6))
        .andExpect(jsonPath("$.entries[1].amount.amountMinor").value(2));
  }

  @Test
  void filtersByDateRange() throws Exception {
    UUID wallet = createWallet("USER_WALLET", "BIF");
    topUp(wallet, 100, "BIF");
    Instant between = Instant.now();
    Thread.sleep(5);
    topUp(wallet, 200, "BIF");

    mvc.perform(get("/api/v1/accounts/{id}/statement?from={from}", wallet, between))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.entries[0].amount.amountMinor").value(200));
    mvc.perform(get("/api/v1/accounts/{id}/statement?to={to}", wallet, between))
        .andExpect(jsonPath("$.totalElements").value(1))
        .andExpect(jsonPath("$.entries[0].amount.amountMinor").value(100));
  }

  @Test
  void rejectsBadParameters() throws Exception {
    UUID wallet = createWallet("USER_WALLET", "BIF");

    mvc.perform(get("/api/v1/accounts/{id}/statement?size=101", wallet))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mvc.perform(get("/api/v1/accounts/{id}/statement?page=-1", wallet))
        .andExpect(status().isBadRequest());
    mvc.perform(
            get(
                "/api/v1/accounts/{id}/statement?from=2026-02-01T00:00:00Z&to=2026-01-01T00:00:00Z",
                wallet))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    mvc.perform(get("/api/v1/accounts/{id}/statement?from=yesterday", wallet))
        .andExpect(status().isBadRequest());
  }

  @Test
  void unknownAccountIs404() throws Exception {
    mvc.perform(get("/api/v1/accounts/{id}/statement", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
  }
}
