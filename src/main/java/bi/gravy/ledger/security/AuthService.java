package bi.gravy.ledger.security;

import bi.gravy.ledger.common.errors.ErrorCode;
import bi.gravy.ledger.common.errors.LedgerException;
import bi.gravy.ledger.security.TokenService.IssuedToken;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  /**
   * A BCrypt hash of a random string. Checking against it when the email is unknown makes a failed
   * login take as long as a wrong password, so response times do not reveal which emails exist.
   */
  private static final String DUMMY_HASH =
      "$2b$10$1F.rlgwKQ5rEW53zo5OFVe1HkSN9uc6Yt.pUvhYYP0T8NRlPdzPDy";

  private final AppUserRepository users;
  private final PasswordEncoder passwordEncoder;
  private final TokenService tokenService;

  public AuthService(
      AppUserRepository users, PasswordEncoder passwordEncoder, TokenService tokenService) {
    this.users = users;
    this.passwordEncoder = passwordEncoder;
    this.tokenService = tokenService;
  }

  public IssuedToken login(String email, String password) {
    Optional<AppUser> user = users.findByEmailIgnoreCase(email);
    String hash = user.map(AppUser::getPasswordHash).orElse(DUMMY_HASH);
    boolean matches = passwordEncoder.matches(password, hash);
    if (user.isEmpty() || !matches) {
      log.info("Failed login attempt");
      // Same answer for "no such user" and "wrong password".
      throw new LedgerException(ErrorCode.INVALID_CREDENTIALS, "Email or password is incorrect");
    }
    log.info("User {} logged in", user.get().getId());
    return tokenService.issue(user.get().getId(), user.get().getEmail(), user.get().getRole());
  }
}
