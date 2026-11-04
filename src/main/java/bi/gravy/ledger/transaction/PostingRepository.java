package bi.gravy.ledger.transaction;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostingRepository extends JpaRepository<Posting, UUID> {

  List<Posting> findByTransactionIdOrderByEntrySeq(UUID transactionId);
}
