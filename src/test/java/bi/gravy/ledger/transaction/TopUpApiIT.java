package bi.gravy.ledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bi.gravy.ledger.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TopUpApiIT extends IntegrationTest {

  @Test
  void creditsTheWalletAndDebitsTopUpClearing() throws Exception {
    UUID wallet = createWallet("USER_WALLET", "BIF");
    UUID clearing = systemAccountId("TOPUP_CLEARING", "BIF");
    long clearingBefore = cachedBalance(clearing);

    topUp(wallet, 20_000, "BIF")
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"))
        .andExpect(jsonPath("$.type").value("TOP_UP"))
        .andExpect(jsonPath("$.externalReference").exists())
        .andExpect(jsonPath("$.postings.length()").value(2))
        .andExpect(jsonPath("$.postings[0].accountId").value(clearing.toString()))
        .andExpect(jsonPath("$.postings[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.postings[1].accountId").value(wallet.toString()))
        .andExpect(jsonPath("$.postings[1].direction").value("CREDIT"))
        .andExpect(jsonPath("$.postings[1].amount.amount").value("20000"));

    assertThat(cachedBalance(wallet)).isEqualTo(20_000);
    assertThat(cachedBalance(clearing)).isEqualTo(clearingBefore - 20_000);
    assertThat(derivedBalance(wallet)).isEqualTo(cachedBalance(wallet));
    assertThat(derivedBalance(clearing)).isEqualTo(cachedBalance(clearing));
  }

  @Test
  void currencyMustMatchTheWallet() throws Exception {
    UUID wallet = createWallet("USER_WALLET", "BIF");

    topUp(wallet, 100, "USD")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    assertThat(postingCount(wallet)).isZero();
  }

  @Test
  void frozenWalletCannotBeToppedUp() throws Exception {
    UUID wallet = createWallet("USER_WALLET", "KES");
    freeze(wallet);

    topUp(wallet, 100, "KES")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("ACCOUNT_FROZEN"));
    assertThat(cachedBalance(wallet)).isZero();
  }

  @Test
  void unknownWalletIs404() throws Exception {
    topUp(UUID.randomUUID(), 100, "BIF")
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_FOUND"));
  }

  @Test
  void systemAccountsCannotBeToppedUpDirectly() throws Exception {
    topUp(systemAccountId("PLATFORM_FEES", "BIF"), 100, "BIF")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void idempotencyKeyHeaderIsRequired() throws Exception {
    UUID wallet = createWallet("USER_WALLET", "BIF");

    postJson(
            "/api/v1/transactions/top-ups",
            null,
            """
            {"accountId": "%s", "amountMinor": 100, "currency": "BIF", "externalReference": "x"}
            """
                .formatted(wallet))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void amountMustBePositive() throws Exception {
    UUID wallet = createWallet("USER_WALLET", "BIF");

    topUp(wallet, 0, "BIF")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
        .andExpect(jsonPath("$.errors[0].field").value("amountMinor"));
    topUp(wallet, -5, "BIF").andExpect(status().isBadRequest());
  }
}
