package bi.gravy.ledger.transaction;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerTransactionRepository extends JpaRepository<LedgerTransaction, UUID> {

  Optional<LedgerTransaction> findByIdempotencyKey(String idempotencyKey);

  boolean existsByReversesTransactionId(UUID reversesTransactionId);
}
