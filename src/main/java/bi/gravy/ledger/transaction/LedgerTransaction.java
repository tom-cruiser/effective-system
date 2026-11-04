package bi.gravy.ledger.transaction;

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

/** A journal entry: the header that groups balanced postings into one business event. */
@Entity
@Immutable
@Table(name = "ledger_transactions")
public class LedgerTransaction {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private TransactionType type;

  @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
  private String idempotencyKey;

  @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
  private String requestHash;

  @Column(length = 255)
  private String description;

  @Column(name = "external_reference", length = 100)
  private String externalReference;

  @Column(name = "reverses_transaction_id")
  private UUID reversesTransactionId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  protected LedgerTransaction() {
    // for JPA
  }

  public LedgerTransaction(
      TransactionType type,
      String idempotencyKey,
      String requestHash,
      String description,
      String externalReference,
      UUID reversesTransactionId) {
    this.type = type;
    this.idempotencyKey = idempotencyKey;
    this.requestHash = requestHash;
    this.description = description;
    this.externalReference = externalReference;
    this.reversesTransactionId = reversesTransactionId;
    this.createdAt = Instant.now();
  }

  public UUID getId() {
    return id;
  }

  public TransactionType getType() {
    return type;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public String getRequestHash() {
    return requestHash;
  }

  public String getDescription() {
    return description;
  }

  public String getExternalReference() {
    return externalReference;
  }

  public UUID getReversesTransactionId() {
    return reversesTransactionId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
