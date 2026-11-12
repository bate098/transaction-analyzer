package com.bucketobate.transactionanalyzer.web;

import com.bucketobate.transactionanalyzer.service.TransactionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/summary")
public class SummaryController {

    private final TransactionService transactionService;

    public SummaryController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @GetMapping
    public TransactionSummaryResponse summary() {
        return transactionService.summarize();
    }
}
