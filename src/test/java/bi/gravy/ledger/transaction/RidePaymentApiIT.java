package bi.gravy.ledger.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bi.gravy.ledger.support.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RidePaymentApiIT extends IntegrationTest {

  @Test
  void splitsTheFareBetweenDriverAndPlatform() throws Exception {
    UUID rider = createFundedWallet("USER_WALLET", "BIF", 15_000);
    UUID driver = createWallet("DRIVER_WALLET", "BIF");
    UUID fees = systemAccountId("PLATFORM_FEES", "BIF");
    long feesBefore = cachedBalance(fees);

    ridePayment(rider, driver, 10_000, "BIF")
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.type").value("RIDE_PAYMENT"))
        .andExpect(jsonPath("$.externalReference").exists())
        .andExpect(jsonPath("$.postings.length()").value(3))
        .andExpect(jsonPath("$.postings[0].direction").value("DEBIT"))
        .andExpect(jsonPath("$.postings[0].amount.amountMinor").value(10_000))
        .andExpect(jsonPath("$.postings[1].accountId").value(driver.toString()))
        .andExpect(jsonPath("$.postings[1].amount.amountMinor").value(8_500))
        .andExpect(jsonPath("$.postings[2].accountId").value(fees.toString()))
        .andExpect(jsonPath("$.postings[2].amount.amountMinor").value(1_500));

    assertThat(cachedBalance(rider)).isEqualTo(5_000).isEqualTo(derivedBalance(rider));
    assertThat(cachedBalance(driver)).isEqualTo(8_500).isEqualTo(derivedBalance(driver));
    assertThat(cachedBalance(fees)).isEqualTo(feesBefore + 1_500);
  }

  @Test
  void driverKeepsTheRoundingRemainder() throws Exception {
    UUID rider = createFundedWallet("USER_WALLET", "USD", 10_000);
    UUID driver = createWallet("DRIVER_WALLET", "USD");

    ridePayment(rider, driver, 1_234, "USD").andExpect(status().isCreated());

    assertThat(cachedBalance(driver)).isEqualTo(1_049); // fee 185.1 cents -> 185
  }

  @Test
  void riderNeedsEnoughFunds() throws Exception {
    UUID rider = createFundedWallet("USER_WALLET", "BIF", 9_999);
    UUID driver = createWallet("DRIVER_WALLET", "BIF");

    ridePayment(rider, driver, 10_000, "BIF")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("INSUFFICIENT_FUNDS"));
    assertThat(cachedBalance(rider)).isEqualTo(9_999);
    assertThat(cachedBalance(driver)).isZero();
  }

  @Test
  void riderAndDriverMustHaveTheRightWalletTypes() throws Exception {
    UUID rider = createFundedWallet("USER_WALLET", "BIF", 10_000);
    UUID otherRider = createWallet("USER_WALLET", "BIF");

    ridePayment(rider, otherRider, 1_000, "BIF")
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
  }

  @Test
  void currencyMustMatchBothWallets() throws Exception {
    UUID rider = createFundedWallet("USER_WALLET", "BIF", 10_000);
    UUID driver = createWallet("DRIVER_WALLET", "RWF");

    ridePayment(rider, driver, 1_000, "BIF")
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
  }
}
