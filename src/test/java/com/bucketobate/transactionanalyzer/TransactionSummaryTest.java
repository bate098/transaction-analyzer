package com.bucketobate.transactionanalyzer;

import com.bucketobate.transactionanalyzer.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TransactionSummaryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    @AfterEach
    void clearTransactions() {
        transactionRepository.deleteAll();
    }

    @Test
    void summaryListsEachAccountId() throws Exception {
        postTransaction("B200", "credit", 20);
        postTransaction("A100", "credit", 500);

        mockMvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaries", hasSize(2)))
                .andExpect(jsonPath("$.summaries[0].accountId").value("A100"))
                .andExpect(jsonPath("$.summaries[1].accountId").value("B200"));
    }

    @Test
    void summaryTotalsCredits() throws Exception {
        postTransaction("A100", "credit", 500);
        postTransaction("A100", "credit", 20);
        postTransaction("A100", "debit", 200);

        mockMvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaries[0].totalCredits").value(520));
    }

    @Test
    void summaryTotalsDebits() throws Exception {
        postTransaction("A100", "debit", 200);
        postTransaction("A100", "debit", 50);
        postTransaction("A100", "credit", 500);

        mockMvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaries[0].totalDebits").value(250));
    }

    @Test
    void summaryComputesEndingBalance() throws Exception {
        postTransaction("A100", "credit", 500);
        postTransaction("A100", "debit", 200);

        mockMvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaries[0].endingBalance").value(300));
    }

    @Test
    void summaryFindsLargestTransaction() throws Exception {
        postTransaction("A100", "credit", 100);
        postTransaction("A100", "debit", 450);
        postTransaction("A100", "credit", 20);

        mockMvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaries[0].largestTransaction").value(450));
    }

    @Test
    void summaryCountsOnlyValidTransactions() throws Exception {
        postTransaction("A100", "credit", 500);
        postTransaction("A100", "debit", 200);
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "accountId": "A100",
                                    "type": "transfer",
                                    "amount": 999,
                                    "timestamp": "2026-05-10T10:00:00Z"
                                  }
                                ]
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaries[0].transactionCount").value(2));
    }

    @Test
    void summaryMarksOverdrawnAccounts() throws Exception {
        postTransaction("A100", "credit", 500);
        postTransaction("A100", "debit", 200);
        postTransaction("B200", "credit", 20);
        postTransaction("B200", "debit", 50);
        postTransaction("C300", "credit", 40);
        postTransaction("C300", "debit", 40);

        mockMvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summaries[0].overdrawn").value(false))
                .andExpect(jsonPath("$.summaries[1].endingBalance").value(-30))
                .andExpect(jsonPath("$.summaries[1].overdrawn").value(true))
                .andExpect(jsonPath("$.summaries[2].overdrawn").value(false));
    }

    @Test
    void summarySelectsAccountWithHighestDebitsAsTopSpender() throws Exception {
        postTransaction("A100", "debit", 10);
        postTransaction("B200", "debit", 80);
        postTransaction("C300", "credit", 500);

        mockMvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topSpender").value("B200"));
    }

    @Test
    void summaryBreaksTopSpenderTiesByAccountId() throws Exception {
        postTransaction("B200", "debit", 40);
        postTransaction("C300", "debit", 5);
        postTransaction("A100", "debit", 40);

        mockMvc.perform(get("/api/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topSpender").value("A100"));
    }

    private void postTransaction(String accountId, String type, int amount) throws Exception {
        mockMvc.perform(post("/api/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [
                                  {
                                    "accountId": "%s",
                                    "type": "%s",
                                    "amount": %d,
                                    "timestamp": "2026-05-10T10:00:00Z"
                                  }
                                ]
                                """.formatted(accountId, type, amount)))
                .andExpect(status().isCreated());
    }
}
