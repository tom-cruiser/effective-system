package bi.gravy.ledger.transaction;

import bi.gravy.ledger.common.Money;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * One line of a journal entry. {@link Immutable} tells Hibernate never to issue an UPDATE for it,
 * and a database trigger rejects UPDATE/DELETE anyway; mistakes are corrected with reversals.
 */
@Entity
@Immutable
@Table(name = "postings")
public class Posting {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  /**
   * Assigned by the database (identity column); gives the true order of postings per account.
   * Read-only here and only populated when a posting is loaded, which is the only time it is
   * needed. (Hibernate cannot re-read generated values into an {@link Immutable} entity.)
   */
  @Column(name = "entry_seq", insertable = false, updatable = false)
  private Long entrySeq;

  @Column(name = "transaction_id", nullable = false)
  private UUID transactionId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 6)
  private Direction direction;

  @Column(name = "amount_minor", nullable = false)
  private long amountMinor;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "balance_after_minor", nullable = false)
  private long balanceAfterMinor;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Posting() {
    // for JPA
  }

  public Posting(
      UUID transactionId,
      UUID accountId,
      Direction direction,
      Money amount,
      long balanceAfterMinor) {
    this.transactionId = transactionId;
    this.accountId = accountId;
    this.direction = direction;
    this.amountMinor = amount.amountMinor();
    this.currency = amount.currency();
    this.balanceAfterMinor = balanceAfterMinor;
    this.createdAt = Instant.now();
  }

  public Money amount() {
    return Money.of(amountMinor, currency);
  }

  public UUID getId() {
    return id;
  }

  public Long getEntrySeq() {
    return entrySeq;
  }

  public UUID getTransactionId() {
    return transactionId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public Direction getDirection() {
    return direction;
  }

  public long getBalanceAfterMinor() {
    return balanceAfterMinor;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
