package com.bucketobate.transactionanalyzer.service;

import com.bucketobate.transactionanalyzer.model.Transaction;
import com.bucketobate.transactionanalyzer.model.TransactionType;
import com.bucketobate.transactionanalyzer.repository.TransactionRepository;
import com.bucketobate.transactionanalyzer.web.AccountSummary;
import com.bucketobate.transactionanalyzer.web.IgnoredTransaction;
import com.bucketobate.transactionanalyzer.web.TransactionBatchResponse;
import com.bucketobate.transactionanalyzer.web.TransactionRequest;
import com.bucketobate.transactionanalyzer.web.TransactionResponse;
import com.bucketobate.transactionanalyzer.web.TransactionSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;

    public TransactionService(TransactionRepository transactionRepository) {
        this.transactionRepository = transactionRepository;
    }

    @Transactional
    public TransactionBatchResponse createAll(List<TransactionRequest> requests) {
        List<TransactionResponse> accepted = new ArrayList<>();
        List<IgnoredTransaction> ignored = new ArrayList<>();
        for (int index = 0; index < requests.size(); index++) {
            TransactionRequest request = requests.get(index);
            String reason = invalidReason(request);
            if (reason != null) {
                ignored.add(new IgnoredTransaction(index, reason));
                continue;
            }
            Transaction transaction = new Transaction(
                    request.accountId().trim(),
                    request.type(),
                    request.amount(),
                    request.timestamp()
            );
            accepted.add(TransactionResponse.from(transactionRepository.save(transaction)));
        }
        return new TransactionBatchResponse(accepted, ignored);
    }

    private String invalidReason(TransactionRequest request) {
        if (request == null || request.accountId() == null || request.accountId().isBlank()) {
            return "accountId is required";
        }
        if (request.type() == null) {
            return "type must be credit or debit";
        }
        if (request.amount() == null || request.amount().compareTo(BigDecimal.ZERO) <= 0) {
            return "amount must be greater than zero";
        }
        if (request.timestamp() == null) {
            return "timestamp is required";
        }
        return null;
    }

    @Transactional(readOnly = true)
    public TransactionSummaryResponse summarize() {
        TreeMap<String, AccountTotals> totalsByAccount = new TreeMap<>();
        for (Transaction transaction : transactionRepository.findAll()) {
            totalsByAccount
                    .computeIfAbsent(transaction.getAccountId(), AccountTotals::new)
                    .add(transaction);
        }

        List<AccountSummary> summaries = new ArrayList<>();
        String topSpender = null;
        BigDecimal highestDebits = null;
        for (AccountTotals totals : totalsByAccount.values()) {
            summaries.add(totals.toSummary());
            if (highestDebits == null || totals.totalDebits.compareTo(highestDebits) > 0) {
                highestDebits = totals.totalDebits;
                topSpender = totals.accountId;
            }
        }
        return new TransactionSummaryResponse(summaries, topSpender);
    }

    @Transactional(readOnly = true)
    public List<TransactionResponse> list(String accountId) {
        List<Transaction> transactions = accountId == null || accountId.isBlank()
                ? transactionRepository.findAllByOrderByTimestampDesc()
                : transactionRepository.findByAccountIdOrderByTimestampDesc(accountId);
        return transactions.stream().map(TransactionResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TransactionResponse get(Long id) {
        return transactionRepository.findById(id)
                .map(TransactionResponse::from)
                .orElseThrow(() -> new TransactionNotFoundException(id));
    }

    @Transactional
    public void delete(Long id) {
        if (!transactionRepository.existsById(id)) {
            throw new TransactionNotFoundException(id);
        }
        transactionRepository.deleteById(id);
    }

    private static final class AccountTotals {
        private final String accountId;
        private BigDecimal totalCredits = BigDecimal.ZERO;
        private BigDecimal totalDebits = BigDecimal.ZERO;
        private BigDecimal largestTransaction = BigDecimal.ZERO;
        private int transactionCount;

        private AccountTotals(String accountId) {
            this.accountId = accountId;
        }

        private void add(Transaction transaction) {
            if (transaction.getType() == TransactionType.CREDIT) {
                totalCredits = totalCredits.add(transaction.getAmount());
            } else if (transaction.getType() == TransactionType.DEBIT) {
                totalDebits = totalDebits.add(transaction.getAmount());
            }
            if (transaction.getAmount().compareTo(largestTransaction) > 0) {
                largestTransaction = transaction.getAmount();
            }
            transactionCount++;
        }

        private AccountSummary toSummary() {
            BigDecimal endingBalance = totalCredits.subtract(totalDebits);
            return new AccountSummary(
                    accountId,
                    totalCredits,
                    totalDebits,
                    endingBalance,
                    largestTransaction,
                    transactionCount,
                    endingBalance.signum() < 0
            );
        }
    }
}
