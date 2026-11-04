package bi.gravy.ledger.common;

import io.swagger.v3.oas.annotations.media.Schema;

/** How money appears in API responses: exact minor units plus a formatted major-unit string. */
public record MoneyResponse(
    @Schema(example = "10000") long amountMinor,
    @Schema(example = "BIF") String currency,
    @Schema(description = "Amount in major units, using the currency's decimals", example = "10000")
        String amount) {

  public static MoneyResponse of(Money money) {
    return new MoneyResponse(money.amountMinor(), money.currency(), money.toDecimalString());
  }
}
