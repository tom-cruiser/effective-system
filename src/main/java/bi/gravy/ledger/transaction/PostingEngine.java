package bi.gravy.ledger.transaction;

import bi.gravy.ledger.account.Account;
import bi.gravy.ledger.account.AccountRepository;
import bi.gravy.ledger.common.errors.ErrorCode;
import bi.gravy.ledger.common.errors.LedgerException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only code that writes postings and changes balances. Business flows decide <em>which</em>
 * postings a transaction needs; the engine guarantees that whatever they ask for keeps the ledger
 * correct.
 *
 * <p>Usage, inside one {@code @Transactional} service method: {@link #lockAccounts} first, then any
 * flow-specific checks on the locked accounts, then {@link #post}. Both methods use {@code
 * MANDATORY} propagation so they fail fast if called outside a transaction, where the locks would
 * be released immediately and protect nothing.
 */
@Component
public class PostingEngine {

  private static final Logger log = LoggerFactory.getLogger(PostingEngine.class);

  private final AccountRepository accounts;
  private final LedgerTransactionRepository transactions;
  private final PostingRepository postings;

  public PostingEngine(
      AccountRepository accounts,
      LedgerTransactionRepository transactions,
      PostingRepository postings) {
    this.accounts = accounts;
    this.transactions = transactions;
    this.postings = postings;
  }

  /**
   * Locks the accounts with {@code SELECT ... FOR UPDATE}, always in ascending id order.
   *
   * <p>If transfer A→B locked A then B while transfer B→A locked B then A, each could end up
   * holding one lock and waiting for the other: a deadlock. When every transaction locks in the
   * same global order, the second one simply waits for the first to finish. {@link UUID#compareTo}
   * is not the same order PostgreSQL uses for UUIDs, which does not matter: all locking goes
   * through here.
   *
   * <p>Call this before anything else reads these accounts in the transaction. Hibernate returns an
   * already-loaded entity as-is, so a copy read before the lock could carry a stale balance.
   *
   * @throws LedgerException ACCOUNT_NOT_FOUND if any id does not exist
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public LockedAccounts lockAccounts(Collection<UUID> accountIds) {
    Map<UUID, Account> locked = new LinkedHashMap<>();
    for (UUID id : new TreeSet<>(accountIds)) {
      Account account =
          accounts.findByIdForUpdate(id).orElseThrow(() -> LedgerException.accountNotFound(id));
      locked.put(id, account);
    }
    return new LockedAccounts(locked);
  }

  /**
   * Validates every ledger invariant for {@code entry}, then stores the transaction, its postings
   * and the new cached balances. Nothing is written unless all checks pass.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public PostedTransaction post(JournalEntry entry, LockedAccounts locked) {
    requireBalancedShape(entry.lines());
    for (PostingLine line : entry.lines()) {
      requirePostable(locked.get(line.accountId()), line);
    }
    requireNoNegativeWallets(entry.lines(), locked);

    LedgerTransaction transaction = transactions.save(entry.toTransaction());
    List<Posting> written = postings.saveAll(applyLines(transaction, entry.lines(), locked));

    log.info(
        "Posted {} transaction {} with {} postings",
        transaction.getType(),
        transaction.getId(),
        written.size());
    return new PostedTransaction(transaction, written);
  }

  /**
   * Invariants 1-3 on the shape of the entry: at least two lines, one currency, positive amounts,
   * debits equal to credits. A violation here means a business flow built a wrong entry, which is a
   * bug rather than a client error, hence {@link IllegalStateException} (HTTP 500).
   */
  static void requireBalancedShape(List<PostingLine> lines) {
    if (lines.size() < 2) {
      throw new IllegalStateException(
          "A transaction needs at least 2 postings, got " + lines.size());
    }
    String currency = lines.get(0).amount().currency();
    long debits = 0;
    long credits = 0;
    for (PostingLine line : lines) {
      if (!line.amount().currency().equals(currency)) {
        throw new IllegalStateException("A transaction cannot mix currencies");
      }
      if (!line.amount().isPositive()) {
        throw new IllegalStateException("Posting amounts must be positive");
      }
      if (line.direction() == Direction.DEBIT) {
        debits = Math.addExact(debits, line.amount().amountMinor());
      } else {
        credits = Math.addExact(credits, line.amount().amountMinor());
      }
    }
    if (debits != credits) {
      throw new IllegalStateException(
          "Unbalanced transaction: debits %d != credits %d".formatted(debits, credits));
    }
  }

  /** Invariants 3 and 6: the account accepts postings and is in the posting's currency. */
  private static void requirePostable(Account account, PostingLine line) {
    if (account.isFrozen()) {
      throw new LedgerException(
          ErrorCode.ACCOUNT_FROZEN, "Account %s is frozen".formatted(line.accountId()));
    }
    if (!account.getCurrency().equals(line.amount().currency())) {
      throw new LedgerException(
          ErrorCode.CURRENCY_MISMATCH,
          "Account %s holds %s, not %s"
              .formatted(line.accountId(), account.getCurrency(), line.amount().currency()));
    }
  }

  /**
   * Invariant 4, checked on the balances the entry would produce. Runs after the locks are held, so
   * no concurrent transaction can spend the same money between this check and the write.
   */
  private static void requireNoNegativeWallets(List<PostingLine> lines, LockedAccounts locked) {
    Map<UUID, Long> projected = new HashMap<>();
    for (PostingLine line : lines) {
      long before =
          projected.getOrDefault(line.accountId(), locked.get(line.accountId()).getBalanceMinor());
      long delta = line.direction().signedAmount(line.amount().amountMinor());
      projected.put(line.accountId(), Math.addExact(before, delta));
    }
    projected.forEach((accountId, balance) -> {
      if (locked.get(accountId).isWallet() && balance < 0) {
        throw new LedgerException(
            ErrorCode.INSUFFICIENT_FUNDS, "Account %s has insufficient funds".formatted(accountId));
      }
    });
  }

  /** Moves the cached balances and records each posting with the balance it produced. */
  private static List<Posting> applyLines(
      LedgerTransaction transaction, List<PostingLine> lines, LockedAccounts locked) {
    List<Posting> result = new ArrayList<>(lines.size());
    for (PostingLine line : lines) {
      Account account = locked.get(line.accountId());
      long balanceAfter =
          account.adjustBalance(line.direction().signedAmount(line.amount().amountMinor()));
      result.add(
          new Posting(
              transaction.getId(),
              line.accountId(),
              line.direction(),
              line.amount(),
              balanceAfter));
    }
    return result;
  }
}
