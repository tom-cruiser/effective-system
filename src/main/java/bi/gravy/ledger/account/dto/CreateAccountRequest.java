package bi.gravy.ledger.account.dto;

import bi.gravy.ledger.account.AccountType;
import bi.gravy.ledger.common.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateAccountRequest(
    @NotNull @Schema(example = "8d0f8e4c-3b9a-4a51-9f7e-2a6f3f1c9b10") UUID ownerId,
    @NotBlank @Size(max = 100) @Schema(example = "Alice's wallet") String name,
    @NotNull
        @Schema(
            example = "USER_WALLET",
            allowableValues = {"USER_WALLET", "DRIVER_WALLET"})
        AccountType type,
    @NotNull @Pattern(regexp = Money.CURRENCY_PATTERN) @Schema(example = "BIF") String currency) {}
