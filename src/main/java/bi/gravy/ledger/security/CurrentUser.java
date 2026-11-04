package bi.gravy.ledger.security;

import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/** The authenticated caller, as described by their validated access token. */
public record CurrentUser(UUID id, boolean admin) {

  public static CurrentUser get() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
      // The filter chain only lets authenticated requests reach controllers.
      throw new IllegalStateException("No authenticated user");
    }
    boolean admin =
        auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + Role.ADMIN.name()));
    return new CurrentUser(UUID.fromString(jwt.getSubject()), admin);
  }
}
