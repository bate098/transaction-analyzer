package com.bucketobate.transactionanalyzer;

import com.bucketobate.transactionanalyzer.repository.TransactionRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void createsListsAndDeletesATransaction() throws Exception {
        String body = """
                [
                  { "accountId": "A100", "type": "credit", "amount": 500, "timestamp": "2026-05-10T10:00:00Z" },
                  { "accountId": "A100", "type": "debit", "amount": 200, "timestamp": "2026-05-10T12:00:00Z" },
                  { "accountId": "B200", "type": "debit", "amount": 50, "timestamp": "2026-05-10T11:00:00Z" },
                  { "accountId": "B200", "type": "credit", "amount": 20, "timestamp": "2026-05-10T13:00:00Z" },
                  { "accountId": "", "type": "credit", "amount": 100, "timestamp": "2026-05-10T14:00:00Z" }
                ]
                """;

        MvcResult created = mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accepted", hasSize(4)))
                .andExpect(jsonPath("$.accepted[0].accountId").value("A100"))
                .andExpect(jsonPath("$.accepted[0].type").value("credit"))
                .andExpect(jsonPath("$.accepted[0].amount").value(500))
                .andExpect(jsonPath("$.accepted[0].timestamp").value("2026-05-10T10:00:00Z"))
                .andExpect(jsonPath("$.ignored", hasSize(1)))
                .andExpect(jsonPath("$.ignored[0].index").value(4))
                .andExpect(jsonPath("$.ignored[0].reason").value("accountId is required"))
                .andReturn();

        Number id = JsonPath.read(created.getResponse().getContentAsString(), "$.accepted[0].id");

        mockMvc.perform(get("/api/transactions").param("accountId", "A100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].accountId").value("A100"));

        mockMvc.perform(delete("/api/transactions/" + id))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/transactions/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    void ignoresTransactionWithMissingOrEmptyAccountId() throws Exception {
        String body = """
                [
                  {
                    "type": "credit",
                    "amount": 500,
                    "timestamp": "2026-05-10T10:00:00Z"
                  },
                  {
                    "accountId": "   ",
                    "type": "credit",
                    "amount": 500,
                    "timestamp": "2026-05-10T10:00:00Z"
                  }
                ]
                """;

        assertAllIgnored(body, "accountId is required", "accountId is required");
    }

    @Test
    void ignoresTransactionWithInvalidType() throws Exception {
        String body = """
                [
                  {
                    "accountId": "A200",
                    "type": "transfer",
                    "amount": 500,
                    "timestamp": "2026-05-10T10:00:00Z"
                  }
                ]
                """;

        assertAllIgnored(body, "type must be credit or debit");
        mockMvc.perform(get("/api/transactions").param("accountId", "A200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void ignoresTransactionWithNonPositiveAmount() throws Exception {
        String body = """
                [
                  {
                    "accountId": "A300",
                    "type": "debit",
                    "amount": 0,
                    "timestamp": "2026-05-10T10:00:00Z"
                  },
                  {
                    "accountId": "A301",
                    "type": "debit",
                    "amount": -25,
                    "timestamp": "2026-05-10T10:00:00Z"
                  }
                ]
                """;

        assertAllIgnored(body, "amount must be greater than zero", "amount must be greater than zero");
        mockMvc.perform(get("/api/transactions").param("accountId", "A300"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/transactions").param("accountId", "A301"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsMalformedTransactionList() throws Exception {
        long countBefore = transactionRepository.count();

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "A100",
                                  "type": "credit",
                                  "amount": 500,
                                  "timestamp": "2026-05-10T10:00:00Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request body is malformed"));

        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Request body is malformed"));

        assertThat(transactionRepository.count()).isEqualTo(countBefore);
    }

    private void assertAllIgnored(String body, String... reasons) throws Exception {
        long countBefore = transactionRepository.count();

        var result = mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.accepted", hasSize(0)))
                .andExpect(jsonPath("$.ignored", hasSize(reasons.length)));
        for (int index = 0; index < reasons.length; index++) {
            result.andExpect(jsonPath("$.ignored[" + index + "].index").value(index))
                    .andExpect(jsonPath("$.ignored[" + index + "].reason").value(reasons[index]));
        }

        assertThat(transactionRepository.count()).isEqualTo(countBefore);
    }
}
