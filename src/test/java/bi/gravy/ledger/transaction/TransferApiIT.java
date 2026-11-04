package bi.gravy.ledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bi.gravy.ledger.support.IntegrationTest;
import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransferApiIT extends IntegrationTest {

  @Test
  void movesMoneyBetweenWallets() throws Exception {
    UUID alice = createFundedWallet("USER_WALLET", "RWF", 1_000);
    UUID bob = createWallet("USER_WALLET", "RWF");

    transfer(alice, bob, 300, "RWF")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("TRANSFER"))
        .andExpect(jsonPath("$.postings[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.postings[1].direction").value("CREDIT"));

    assertThat(cachedBalance(alice)).isEqualTo(700).isEqualTo(derivedBalance(alice));
    assertThat(cachedBalance(bob)).isEqualTo(300).isEqualTo(derivedBalance(bob));
  }

  @Test
  void insufficientFundsChangesNothing() throws Exception {
    UUID alice = createFundedWallet("USER_WALLET", "BIF", 100);
    UUID bob = createWallet("DRIVER_WALLET", "BIF");
    long alicePostings = postingCount(alice);

    transfer(alice, bob, 101, "BIF")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));

    assertThat(cachedBalance(alice)).isEqualTo(100);
    assertThat(cachedBalance(bob)).isZero();
    assertThat(postingCount(alice)).isEqualTo(alicePostings);
    assertThat(postingCount(bob)).isZero();
  }

  @Test
  void cannotTransferToTheSameAccount() throws Exception {
    UUID alice = createFundedWallet("USER_WALLET", "BIF", 100);

    transfer(alice, alice, 10, "BIF")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("SAME_ACCOUNT_TRANSFER"));
  }

  @Test
  void walletsMustShareTheTransferCurrency() throws Exception {
    UUID bif = createFundedWallet("USER_WALLET", "BIF", 100);
    UUID usd = createWallet("USER_WALLET", "USD");

    transfer(bif, usd, 10, "BIF")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
    assertThat(cachedBalance(bif)).isEqualTo(100);
  }

  @Test
  void frozenSourceOrDestinationIsRejected() throws Exception {
    UUID alice = createFundedWallet("USER_WALLET", "BIF", 100);
    UUID bob = createFundedWallet("USER_WALLET", "BIF", 100);
    freeze(bob);

    transfer(alice, bob, 10, "BIF")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("ACCOUNT_FROZEN"));
    transfer(bob, alice, 10, "BIF")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("ACCOUNT_FROZEN"));
  }

  @Test
  void cannotTransferOutOfASystemAccount() throws Exception {
    UUID fees = systemAccountId("PLATFORM_FEES", "BIF");
    UUID alice = createWallet("USER_WALLET", "BIF");

    transfer(fees, alice, 10, "BIF")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void transactionCanBeReadBackWithItsPostings() throws Exception {
    UUID alice = createFundedWallet("USER_WALLET", "BIF", 500);
    UUID bob = createWallet("USER_WALLET", "BIF");
    String created =
        transfer(alice, bob, 200, "BIF").andReturn().getResponse().getContentAsString();
    String id = JsonPath.read(created, "$.id");

    mvc.perform(get("/api/v1/transactions/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.postings.length()").value(2))
        .andExpect(jsonPath("$.postings[0].accountId").value(alice.toString()))
        .andExpect(jsonPath("$.postings[1].amount.amountMinor").value(200));
  }

  @Test
  void unknownTransactionIs404() throws Exception {
    mvc.perform(get("/api/v1/transactions/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
  }
}
