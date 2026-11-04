package bi.gravy.ledger.transaction.dto;

import bi.gravy.ledger.common.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record TransferRequest(
    @NotNull UUID fromAccountId,
    @NotNull UUID toAccountId,
    @Positive @Schema(description = "Amount in minor units", example = "5000") long amountMinor,
    @NotNull @Pattern(regexp = Money.CURRENCY_PATTERN) @Schema(example = "BIF") String currency,
    @Size(max = 255) @Schema(example = "Splitting the fare") String description) {}
