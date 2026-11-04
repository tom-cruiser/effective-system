package bi.gravy.ledger.common.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed {@code ledger.*} settings from application.yml. {@link Validated} makes the application
 * refuse to start with a nonsensical value instead of failing on the first ride.
 *
 * @param platformFeeBps the platform's commission on rides in basis points (1500 = 15%)
 */
@Validated
@ConfigurationProperties(prefix = "ledger")
public record LedgerProperties(@Min(0) @Max(10_000) int platformFeeBps) {}
