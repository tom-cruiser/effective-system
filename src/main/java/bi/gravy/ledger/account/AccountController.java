package bi.gravy.ledger.account;

import bi.gravy.ledger.account.dto.AccountResponse;
import bi.gravy.ledger.account.dto.CreateAccountRequest;
import bi.gravy.ledger.security.AccessGuard;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
public class AccountController {

  private final AccountService accountService;
  private final AccessGuard accessGuard;

  public AccountController(AccountService accountService, AccessGuard accessGuard) {
    this.accountService = accountService;
    this.accessGuard = accessGuard;
  }

  @PostMapping
  @Operation(summary = "Create a rider or driver wallet")
  public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
    Account account = accountService.create(request, accessGuard.resolveOwner(request.ownerId()));
    return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId()))
        .body(AccountResponse.from(account));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get an account and its current balance")
  public AccountResponse get(@PathVariable UUID id) {
    accessGuard.requireAccountAccess(id);
    return AccountResponse.from(accountService.get(id));
  }
}
