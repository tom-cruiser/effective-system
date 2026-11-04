package bi.gravy.ledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bi.gravy.ledger.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReversalApiIT extends IntegrationTest {

  @Test
  void reversingARidePaymentRestoresEveryBalance() throws Exception {
    UUID rider = createFundedWallet("USER_WALLET", "BIF", 10_000);
    UUID driver = createWallet("DRIVER_WALLET", "BIF");
    UUID fees = systemAccountId("PLATFORM_FEES", "BIF");
    long feesBefore = cachedBalance(fees);
    String rideId = idOf(ridePayment(rider, driver, 10_000, "BIF"));

    reverse(rideId)
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("REVERSAL"))
        .andExpect(jsonPath("$.reversesTransactionId").value(rideId))
        .andExpect(jsonPath("$.description").value("Reversal of " + rideId + ": test"))
        .andExpect(jsonPath("$.postings.length()").value(3))
        .andExpect(jsonPath("$.postings[0].accountId").value(rider.toString()))
        .andExpect(jsonPath("$.postings[0].direction").value("CREDIT"))
        .andExpect(jsonPath("$.postings[1].direction").value("DEBIT"))
        .andExpect(jsonPath("$.postings[2].direction").value("DEBIT"));

    assertThat(cachedBalance(rider)).isEqualTo(10_000).isEqualTo(derivedBalance(rider));
    assertThat(cachedBalance(driver)).isZero().isEqualTo(derivedBalance(driver));
    assertThat(cachedBalance(fees)).isEqualTo(feesBefore);
    // History is kept: 1 top-up + 1 ride + 1 reversal posting on the rider's account.
    assertThat(postingCount(rider)).isEqualTo(3);
  }

  @Test
  void aTransactionCanOnlyBeReversedOnce() throws Exception {
    UUID alice = createFundedWallet("USER_WALLET", "BIF", 100);
    UUID bob = createWallet("USER_WALLET", "BIF");
    String transferId = idOf(transfer(alice, bob, 40, "BIF"));
    reverse(transferId).andExpect(status().isCreated());

    reverse(transferId)
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ALREADY_REVERSED"));
    assertThat(cachedBalance(alice)).isEqualTo(100);
    assertThat(cachedBalance(bob)).isZero();
  }

  @Test
  void aReversalCannotBeReversed() throws Exception {
    UUID alice = createFundedWallet("USER_WALLET", "BIF", 100);
    UUID bob = createWallet("USER_WALLET", "BIF");
    String reversalId = idOf(reverse(idOf(transfer(alice, bob, 40, "BIF"))));

    reverse(reversalId)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void reversalStillRespectsWalletBalances() throws Exception {
    // Alice tops up 100 and sends it all to Bob; reversing the top-up would make her negative.
    UUID alice = createWallet("USER_WALLET", "BIF");
    String topUpId = idOf(topUp(alice, 100, "BIF"));
    transfer(alice, createWallet("USER_WALLET", "BIF"), 100, "BIF");

    reverse(topUpId)
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
  }

  @Test
  void unknownTransactionIs404() throws Exception {
    reverse(UUID.randomUUID().toString())
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value("TRANSACTION_NOT_FOUND"));
  }

  @Test
  void reasonIsOptional() throws Exception {
    UUID alice = createFundedWallet("USER_WALLET", "BIF", 100);
    String transferId = idOf(transfer(alice, createWallet("USER_WALLET", "BIF"), 1, "BIF"));

    postJson("/api/v1/transactions/" + transferId + "/reversal", newKey(), "")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.description").value("Reversal of " + transferId));
  }
}
