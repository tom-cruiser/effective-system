package bi.gravy.ledger.account;

import bi.gravy.ledger.account.dto.CreateAccountRequest;
import bi.gravy.ledger.common.errors.LedgerException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

  private final AccountRepository accounts;

  public AccountService(AccountRepository accounts) {
    this.accounts = accounts;
  }

  /** Only wallets can be created through the API; system accounts come from migrations. */
  @Transactional
  public Account create(CreateAccountRequest request) {
    if (!request.type().isWallet()) {
      throw LedgerException.validation(
          "Only USER_WALLET and DRIVER_WALLET accounts can be created");
    }
    return accounts.save(
        new Account(request.ownerId(), request.name(), request.type(), request.currency()));
  }

  @Transactional(readOnly = true)
  public Account get(UUID id) {
    return accounts.findById(id).orElseThrow(() -> LedgerException.accountNotFound(id));
  }

  @Transactional(readOnly = true)
  public UUID systemAccountId(SystemAccount account, String currency) {
    return accounts
        .findSystemAccountId(account.name(), currency)
        .orElseThrow(() ->
            new IllegalStateException("Missing system account %s/%s".formatted(account, currency)));
  }
}
