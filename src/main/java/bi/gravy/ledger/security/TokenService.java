package bi.gravy.ledger.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

/** Issues signed access tokens. Reading them back is Spring Security's job (see SecurityConfig). */
@Service
public class TokenService {

  /** Name of the claim carrying the user's roles, mapped to ROLE_* authorities on the way in. */
  public static final String ROLES_CLAIM = "roles";

  private final JwtEncoder encoder;
  private final JwtProperties properties;

  public TokenService(JwtEncoder encoder, JwtProperties properties) {
    this.encoder = encoder;
    this.properties = properties;
  }

  public record IssuedToken(String value, long expiresInSeconds) {}

  public IssuedToken issue(UUID userId, String email, Role role) {
    return issue(userId, email, role, Instant.now());
  }

  /** {@code issuedAt} is a parameter so tests can mint already-expired tokens. */
  public IssuedToken issue(UUID userId, String email, Role role, Instant issuedAt) {
    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .subject(userId.toString())
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plus(properties.tokenTtl()))
            .claim("email", email)
            .claim(ROLES_CLAIM, List.of(role.name()))
            .build();
    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
    String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    return new IssuedToken(value, properties.tokenTtl().toSeconds());
  }
}
