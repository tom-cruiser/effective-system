package bi.gravy.ledger.account;

import static bi.gravy.ledger.common.errors.ErrorCode.ACCESS_DENIED;
import static bi.gravy.ledger.common.errors.ErrorCode.ACCOUNT_NOT_FOUND;

import bi.gravy.ledger.account.dto.AccountResponse;
import bi.gravy.ledger.account.dto.CreateAccountRequest;
import bi.gravy.ledger.common.errors.ApiErrors;
import bi.gravy.ledger.security.AccessGuard;
import bi.gravy.ledger.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
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
  @ApiResponse(responseCode = "201", description = "Created")
  @ApiErrors({ACCESS_DENIED})
  public ResponseEntity<AccountResponse> create(@Valid @RequestBody CreateAccountRequest request) {
    Account account = accountService.create(request, accessGuard.resolveOwner(request.ownerId()));
    return ResponseEntity.created(URI.create("/api/v1/accounts/" + account.getId()))
        .body(AccountResponse.from(account));
  }

  /** Always the caller's own accounts, so it needs no ownership check (admins own none). */
  @GetMapping
  @Operation(summary = "List the caller's own wallets, oldest first")
  public List<AccountResponse> mine() {
    return accountService.ownedBy(CurrentUser.get().id()).stream()
        .map(AccountResponse::from)
        .toList();
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get an account and its current balance")
  @ApiErrors({ACCESS_DENIED, ACCOUNT_NOT_FOUND})
  public AccountResponse get(@PathVariable UUID id) {
    accessGuard.requireAccountAccess(id);
    return AccountResponse.from(accountService.get(id));
  }
}
