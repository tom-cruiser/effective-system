package bi.gravy.ledger.transaction.dto;

import bi.gravy.ledger.common.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TransferRequest(
    @NotNull
        @Schema(description = "Sender's wallet", example = "0b6f1c1e-6a52-4c3e-9a51-2f1d8d7c4a10")
        UUID fromAccountId,
    @NotNull
        @Schema(
            description = "Recipient's wallet",
            example = "c4a8e2f1-9b7d-4e3a-a6c5-1d2f3e4b5a67")
        UUID toAccountId,
    @Positive @Schema(description = "Amount in minor units", example = "5000") long amountMinor,
    @NotNull @Pattern(regexp = Money.CURRENCY_PATTERN) @Schema(example = "BIF") String currency,
    @Size(max = 255) @Schema(example = "Splitting the fare") String description) {}
