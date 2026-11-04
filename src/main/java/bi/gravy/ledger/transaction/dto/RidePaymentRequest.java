package bi.gravy.ledger.transaction.dto;

import bi.gravy.ledger.common.Money;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record RidePaymentRequest(
    @NotNull
        @Schema(
            description = "The rider's USER_WALLET",
            example = "0b6f1c1e-6a52-4c3e-9a51-2f1d8d7c4a10")
        UUID riderAccountId,
    @NotNull
        @Schema(
            description = "The driver's DRIVER_WALLET",
            example = "7d2e9c4b-1f3a-4b6d-8e2c-5a9f0b3d6e21")
        UUID driverAccountId,
    @Positive @Schema(description = "Fare in minor units", example = "10000") long fareMinor,
    @NotNull @Pattern(regexp = Money.CURRENCY_PATTERN) @Schema(example = "BIF") String currency,
    @NotBlank @Size(max = 100) @Schema(description = "The ride's id", example = "ride-2041")
        String rideId,
    @Size(max = 255) @Schema(example = "Kinindo to city centre") String description) {}
