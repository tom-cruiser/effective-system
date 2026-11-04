package bi.gravy.ledger.reconciliation;

import static bi.gravy.ledger.common.errors.ErrorCode.ACCESS_DENIED;

import bi.gravy.ledger.common.errors.ApiErrors;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin")
public class ReconciliationController {

  private final ReconciliationService reconciliationService;

  public ReconciliationController(ReconciliationService reconciliationService) {
    this.reconciliationService = reconciliationService;
  }

  @GetMapping("/reconciliation")
  @Operation(
      summary = "Check the ledger's integrity (ADMIN only)",
      description =
          "Verifies that debits equal credits per currency and per transaction, and that every"
              + " cached balance equals the balance derived from postings.")
  @ApiErrors({ACCESS_DENIED})
  public ReconciliationReport reconcile() {
    return reconciliationService.reconcile();
  }
}
