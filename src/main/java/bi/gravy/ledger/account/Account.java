package bi.gravy.ledger.account;

import bi.gravy.ledger.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts")
public class Account {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Column(name = "owner_id")
  private UUID ownerId;

  @Column(nullable = false, length = 100)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private AccountType type;

  @Column(nullable = false, length = 3)
  private String currency;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 10)
  private AccountStatus status;

  /** Cached sum of this account's postings, so reading a balance does not scan its history. */
  @Column(name = "balance_minor", nullable = false)
  private long balanceMinor;

  /**
   * Bumped on every update. Ledger writes already hold a row lock, so this is a safety net: if an
   * account were ever updated from a stale copy, the UPDATE would match no row and fail instead of
   * silently overwriting a newer balance.
   */
  @Version private Long version;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected Account() {
    // for JPA
  }

  public Account(UUID ownerId, String name, AccountType type, String currency) {
    this.ownerId = ownerId;
    this.name = name;
    this.type = type;
    this.currency = currency;
    this.status = AccountStatus.ACTIVE;
    this.balanceMinor = 0;
    this.createdAt = Instant.now();
  }

  /**
   * Moves the cached balance by one posting's signed amount and returns the new balance. Only
   * {@code PostingEngine} calls this, after it has locked the account and checked every invariant.
   * (It takes a signed amount rather than a {@code Direction} so this package does not depend on
   * the transaction package.)
   */
  public long adjustBalance(long signedAmountMinor) {
    balanceMinor = Math.addExact(balanceMinor, signedAmountMinor);
    return balanceMinor;
  }

  public boolean isWallet() {
    return type.isWallet();
  }

  public boolean isFrozen() {
    return status == AccountStatus.FROZEN;
  }

  public Money balance() {
    return Money.of(balanceMinor, currency);
  }

  public UUID getId() {
    return id;
  }

  public UUID getOwnerId() {
    return ownerId;
  }

  public String getName() {
    return name;
  }

  public AccountType getType() {
    return type;
  }

  public String getCurrency() {
    return currency;
  }

  public AccountStatus getStatus() {
    return status;
  }

  public long getBalanceMinor() {
    return balanceMinor;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
