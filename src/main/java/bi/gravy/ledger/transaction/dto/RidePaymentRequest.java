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
    @NotNull @Schema(description = "The rider's USER_WALLET") UUID riderAccountId,
    @NotNull @Schema(description = "The driver's DRIVER_WALLET") UUID driverAccountId,
    @Positive @Schema(description = "Fare in minor units", example = "10000") long fareMinor,
    @NotNull @Pattern(regexp = Money.CURRENCY_PATTERN) @Schema(example = "BIF") String currency,
    @NotBlank @Size(max = 100) @Schema(description = "The ride's id", example = "ride-2041")
        String rideId,
    @Size(max = 255) @Schema(example = "Kinindo to city centre") String description) {}
