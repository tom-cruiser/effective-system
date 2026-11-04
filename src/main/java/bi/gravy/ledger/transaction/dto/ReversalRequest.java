package bi.gravy.ledger.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record ReversalRequest(
    @Size(max = 200) @Schema(example = "Rider was charged for a cancelled ride") String reason) {}
