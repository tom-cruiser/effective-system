package bi.gravy.ledger.statement;

import bi.gravy.ledger.account.Account;
import bi.gravy.ledger.account.AccountService;
import bi.gravy.ledger.common.errors.LedgerException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatementService {

  /** Open-ended bounds when the client gives no date range. */
  static final Instant BEGINNING = Instant.EPOCH;

  static final Instant END = Instant.parse("9999-12-31T23:59:59Z");

  private final AccountService accountService;
  private final StatementRepository statements;

  public StatementService(AccountService accountService, StatementRepository statements) {
    this.accountService = accountService;
    this.statements = statements;
  }

  /** The account (for its current balance) and one page of its postings. */
  public record Statement(Account account, Page<StatementLine> lines) {}

  @Transactional(readOnly = true)
  public Statement statement(UUID accountId, Instant from, Instant to, int page, int size) {
    Instant start = from != null ? from : BEGINNING;
    Instant end = to != null ? to : END;
    if (!start.isBefore(end)) {
      throw LedgerException.validation("'from' must be before 'to'");
    }
    Account account = accountService.get(accountId);
    return new Statement(
        account, statements.findLines(accountId, start, end, PageRequest.of(page, size)));
  }
}
