package bi.gravy.ledger.transaction.dto;

import bi.gravy.ledger.common.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record PayoutRequest(
    @NotNull @Schema(description = "The driver's DRIVER_WALLET") UUID driverAccountId,
    @Positive @Schema(description = "Amount in minor units", example = "8500") long amountMinor,
    @NotNull @Pattern(regexp = Money.CURRENCY_PATTERN) @Schema(example = "BIF") String currency,
    @Size(max = 100)
        @Schema(
            description = "Mobile-money payout reference, if already known",
            example = "PAYOUT-551")
        String externalReference,
    @Size(max = 255) @Schema(example = "Weekly payout") String description) {}
