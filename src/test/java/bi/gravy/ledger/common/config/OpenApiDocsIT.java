package bi.gravy.ledger.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import bi.gravy.ledger.support.IntegrationTest;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The OpenAPI document behind Swagger UI documents real statuses and error examples. */
class OpenApiDocsIT extends IntegrationTest {

  private DocumentContext docs;

  @BeforeEach
  void loadDocs() throws Exception {
    String json =
        anonymousMvc
            .perform(get("/v3/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    docs = JsonPath.parse(json);
  }

  @Test
  void moneyMovingEndpointsDocumentCreatedAndReplay() {
    Map<String, Object> responses =
        docs.read("$.paths['/api/v1/transactions/transfers'].post.responses");
    assertThat(responses).containsKeys("201", "200", "400", "401", "403", "404", "409", "422");
  }

  @Test
  void errorResponsesCarryProblemExamplesWithTheirCode() {
    String path =
        "$.paths['/api/v1/transactions/transfers'].post.responses['422']"
            + ".content['application/problem+json'].examples";
    Map<String, Object> examples = docs.read(path);
    assertThat(examples)
        .containsOnlyKeys(
            "INSUFFICIENT_FUNDS", "CURRENCY_MISMATCH", "ACCOUNT_FROZEN", "SAME_ACCOUNT_TRANSFER");
    assertThat((String) docs.read(path + ".INSUFFICIENT_FUNDS.value.code"))
        .isEqualTo("INSUFFICIENT_FUNDS");
    assertThat((Integer) docs.read(path + ".INSUFFICIENT_FUNDS.value.status")).isEqualTo(422);
  }

  @Test
  void loginNeedsNoTokenSoItsOnly401IsInvalidCredentials() {
    Map<String, Object> examples =
        docs.read(
            "$.paths['/api/v1/auth/login'].post.responses['401']"
                + ".content['application/problem+json'].examples");
    assertThat(examples).containsOnlyKeys("INVALID_CREDENTIALS");
  }

  @Test
  void everyOperationHasASummaryAndDocumentsItsErrors() {
    List<Map<String, Object>> operations = docs.read("$.paths.*.*");
    assertThat(operations).hasSize(12);
    assertThat(operations)
        .allSatisfy(operation -> {
          assertThat(operation).containsKey("summary");
          @SuppressWarnings("unchecked")
          Map<String, Object> responses = (Map<String, Object>) operation.get("responses");
          assertThat(responses.keySet()).anyMatch(code -> code.startsWith("4"));
        });
  }
}
