package bi.gravy.ledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bi.gravy.ledger.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PayoutApiIT extends IntegrationTest {

  @Test
  void debitsTheDriverAndCreditsPayoutClearing() throws Exception {
    UUID driver = createFundedWallet("DRIVER_WALLET", "KES", 50_000);
    UUID clearing = systemAccountId("PAYOUT_CLEARING", "KES");
    long clearingBefore = cachedBalance(clearing);

    payout(driver, 20_000, "KES")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("PAYOUT"))
        .andExpect(jsonPath("$.postings[0].accountId").value(driver.toString()))
        .andExpect(jsonPath("$.postings[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.postings[1].accountId").value(clearing.toString()))
        .andExpect(jsonPath("$.postings[1].amount.amount").value("200.00"));

    assertThat(cachedBalance(driver)).isEqualTo(30_000).isEqualTo(derivedBalance(driver));
    assertThat(cachedBalance(clearing)).isEqualTo(clearingBefore + 20_000);
  }

  @Test
  void cannotPayOutMoreThanTheBalance() throws Exception {
    UUID driver = createFundedWallet("DRIVER_WALLET", "KES", 100);

    payout(driver, 101, "KES")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
  }

  @Test
  void onlyDriverWalletsArePaidOut() throws Exception {
    UUID rider = createFundedWallet("USER_WALLET", "KES", 100);

    payout(rider, 50, "KES")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }
}
