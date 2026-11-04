package bi.gravy.ledger.statement;

import bi.gravy.ledger.transaction.Posting;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface StatementRepository extends Repository<Posting, UUID> {

  /**
   * An account's postings in {@code [from, to)}, newest first. Ordered by {@code entrySeq}, the
   * order the postings were actually applied, so each row's {@code balanceAfterMinor} reads as a
   * running balance. Served by the (account_id, entry_seq) index.
   */
  @Query(
      value =
          """
          select new bi.gravy.ledger.statement.StatementLine(
              p.id, p.transactionId, t.type, p.direction, p.amountMinor, p.currency,
              p.balanceAfterMinor, t.description, p.createdAt)
          from Posting p join LedgerTransaction t on t.id = p.transactionId
          where p.accountId = :accountId and p.createdAt >= :from and p.createdAt < :to
          order by p.entrySeq desc
          """,
      countQuery =
          """
          select count(p) from Posting p
          where p.accountId = :accountId and p.createdAt >= :from and p.createdAt < :to
          """)
  Page<StatementLine> findLines(UUID accountId, Instant from, Instant to, Pageable pageable);
}
