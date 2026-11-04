package bi.gravy.ledger.transaction.dto;

import bi.gravy.ledger.common.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TopUpRequest(
    @NotNull
        @Schema(description = "Wallet to credit", example = "0b6f1c1e-6a52-4c3e-9a51-2f1d8d7c4a10")
        UUID accountId,
    @Positive @Schema(description = "Amount in minor units", example = "20000") long amountMinor,
    @NotNull @Pattern(regexp = Money.CURRENCY_PATTERN) @Schema(example = "BIF") String currency,
    @NotBlank
        @Size(max = 100)
        @Schema(description = "Mobile-money provider's transaction id", example = "LUMICASH-7731")
        String externalReference,
    @Size(max = 255) @Schema(example = "Lumicash top-up") String description) {}
