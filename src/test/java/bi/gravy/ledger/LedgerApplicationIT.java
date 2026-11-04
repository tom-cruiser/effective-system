package bi.gravy.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bi.gravy.ledger.support.IntegrationTest;
import org.junit.jupiter.api.Test;

class LedgerApplicationIT extends IntegrationTest {

  @Test
  void healthEndpointReportsUp() throws Exception {
    mvc.perform(get("/actuator/health"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("UP"));
  }

  @Test
  void flywaySeedsThreeSystemAccountsPerCurrency() {
    Integer count =
        jdbc.queryForObject("SELECT count(*) FROM accounts WHERE type = 'SYSTEM'", Integer.class);

    assertThat(count).isEqualTo(3 * 4);
  }

  @Test
  void openApiDocumentIsServed() throws Exception {
    mvc.perform(get("/v3/api-docs"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.paths['/api/v1/transactions/transfers']").exists());
  }
}
