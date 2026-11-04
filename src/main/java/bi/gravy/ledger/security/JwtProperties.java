package bi.gravy.ledger.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param secret HMAC key for HS256; at least 32 bytes (256 bits), as the algorithm requires
 * @param issuer value of the {@code iss} claim, checked when tokens are read back
 * @param tokenTtl how long an access token is valid
 */
@Validated
@ConfigurationProperties(prefix = "ledger.security.jwt")
public record JwtProperties(
    @NotBlank @Size(min = 32) String secret, @NotBlank String issuer, @NotNull Duration tokenTtl) {}
