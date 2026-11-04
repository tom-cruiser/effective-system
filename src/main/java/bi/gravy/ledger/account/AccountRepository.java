package bi.gravy.ledger.account;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface AccountRepository extends JpaRepository<Account, UUID> {

  /**
   * {@code SELECT ... FOR UPDATE}: takes a row lock that other writers (and other {@code FOR
   * UPDATE} readers) wait on until this database transaction commits or rolls back.
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from Account a where a.id = :id")
  Optional<Account> findByIdForUpdate(UUID id);

  /** Only the id, so the entity is not loaded (and cached) before the engine locks it. */
  @Query(
      """
      select a.id from Account a
      where a.type = bi.gravy.ledger.account.AccountType.SYSTEM
        and a.name = :name and a.currency = :currency
      """)
  Optional<UUID> findSystemAccountId(String name, String currency);
}
