package bi.gravy.ledger.security;

import static bi.gravy.ledger.common.errors.ErrorCode.INVALID_CREDENTIALS;

import bi.gravy.ledger.common.errors.ApiErrors;
import bi.gravy.ledger.security.TokenService.IssuedToken;
import bi.gravy.ledger.security.dto.LoginRequest;
import bi.gravy.ledger.security.dto.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Auth")
public class AuthController {

  private final AuthService authService;

  public AuthController(AuthService authService) {
    this.authService = authService;
  }

  @PostMapping("/login")
  @SecurityRequirements // no token needed to get a token
  @Operation(summary = "Exchange email and password for a JWT access token")
  @ApiErrors({INVALID_CREDENTIALS})
  public TokenResponse login(@Valid @RequestBody LoginRequest request) {
    IssuedToken token = authService.login(request.email(), request.password());
    return TokenResponse.bearer(token.value(), token.expiresInSeconds());
  }
}
