package bi.gravy.ledger.account.dto;

import bi.gravy.ledger.account.Account;
import bi.gravy.ledger.account.AccountStatus;
import bi.gravy.ledger.account.AccountType;
import bi.gravy.ledger.common.MoneyResponse;
import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    UUID ownerId,
    String name,
    AccountType type,
    String currency,
    AccountStatus status,
    MoneyResponse balance,
    Instant createdAt) {

  public static AccountResponse from(Account account) {
    return new AccountResponse(
        account.getId(),
        account.getOwnerId(),
        account.getName(),
        account.getType(),
        account.getCurrency(),
        account.getStatus(),
        MoneyResponse.of(account.balance()),
        account.getCreatedAt());
  }
}
