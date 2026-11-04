package bi.gravy.ledger.statement.dto;

import bi.gravy.ledger.common.MoneyResponse;
import bi.gravy.ledger.statement.StatementService.Statement;
import java.util.List;
import java.util.UUID;

public record StatementResponse(
    UUID accountId,
    String currency,
    MoneyResponse currentBalance,
    int page,
    int size,
    long totalElements,
    int totalPages,
    List<StatementEntryResponse> entries) {

  public static StatementResponse from(Statement statement) {
    var lines = statement.lines();
    return new StatementResponse(
        statement.account().getId(),
        statement.account().getCurrency(),
        MoneyResponse.of(statement.account().balance()),
        lines.getNumber(),
        lines.getSize(),
        lines.getTotalElements(),
        lines.getTotalPages(),
        lines.getContent().stream().map(StatementEntryResponse::from).toList());
  }
}
