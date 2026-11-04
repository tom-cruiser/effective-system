package bi.gravy.ledger.statement;

import static bi.gravy.ledger.common.errors.ErrorCode.ACCESS_DENIED;
import static bi.gravy.ledger.common.errors.ErrorCode.ACCOUNT_NOT_FOUND;

import bi.gravy.ledger.common.errors.ApiErrors;
import bi.gravy.ledger.security.AccessGuard;
import bi.gravy.ledger.statement.dto.StatementResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Instant;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
@Tag(name = "Accounts")
public class StatementController {

  private final StatementService statementService;
  private final AccessGuard accessGuard;

  public StatementController(StatementService statementService, AccessGuard accessGuard) {
    this.statementService = statementService;
    this.accessGuard = accessGuard;
  }

  @GetMapping("/{id}/statement")
  @Operation(
      summary = "Account statement",
      description = "Postings newest first, each with the account balance right after it.")
  @ApiErrors({ACCESS_DENIED, ACCOUNT_NOT_FOUND})
  public StatementResponse statement(
      @PathVariable UUID id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
      @Parameter(
              description = "Inclusive lower bound (ISO-8601 instant)",
              example = "2026-01-01T00:00:00Z")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @Parameter(description = "Exclusive upper bound (ISO-8601 instant)")
          @RequestParam(required = false)
          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to) {
    accessGuard.requireAccountAccess(id);
    return StatementResponse.from(statementService.statement(id, from, to, page, size));
  }
}
