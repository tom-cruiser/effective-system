package bi.gravy.ledger.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/** API metadata, plus the bearer scheme that gives Swagger UI its "Authorize" button. */
@Configuration
@OpenAPIDefinition(
    info =
        @Info(
            title = "Wallet Ledger API",
            version = "v1",
            description = "Double-entry wallet ledger for a ride-sharing platform.",
            license = @License(name = "MIT")),
    security = @SecurityRequirement(name = "bearerAuth"))
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT")
public class OpenApiConfig {}
