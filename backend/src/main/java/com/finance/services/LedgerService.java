package com.finance.services;

import com.finance.entities.Account;
import com.finance.entities.Category;
import com.finance.entities.Transaction;
import com.finance.entities.TransactionType;
import com.finance.entities.User;
import com.finance.repositories.AccountRepository;
import com.finance.repositories.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;

    @Transactional
    public Transaction createTransaction(User user,
                                         Account account,
                                         Category category,
                                         TransactionType type,
                                         BigDecimal amount,
                                         LocalDate transactionDate,
                                         String merchant,
                                         String note,
                                         String paymentMethod,
                                         UUID transferGroupId) {
        applyEffect(account, type, amount);
        try {
            accountRepository.saveAndFlush(account);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw exception;
        }

        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setAccount(account);
        transaction.setCategory(category);
        transaction.setType(type);
        transaction.setAmount(amount);
        transaction.setTransactionDate(transactionDate);
        transaction.setMerchant(merchant);
        transaction.setNote(note);
        transaction.setPaymentMethod(paymentMethod);
        transaction.setTransferGroupId(transferGroupId);
        return transactionRepository.saveAndFlush(transaction);
    }

    @Transactional
    public void reverseTransaction(Transaction transaction) {
        Account account = transaction.getAccount();
        reverseEffect(account, transaction.getType(), transaction.getAmount());
        accountRepository.saveAndFlush(account);
    }

    public void applyEffect(Account account, TransactionType type, BigDecimal amount) {
        account.setCurrentBalance(switch (type) {
            case INCOME, TRANSFER_IN -> account.getCurrentBalance().add(amount);
            case EXPENSE, TRANSFER_OUT -> account.getCurrentBalance().subtract(amount);
        });
    }

    public void reverseEffect(Account account, TransactionType type, BigDecimal amount) {
        account.setCurrentBalance(switch (type) {
            case INCOME, TRANSFER_IN -> account.getCurrentBalance().subtract(amount);
            case EXPENSE, TRANSFER_OUT -> account.getCurrentBalance().add(amount);
        });
    }
}
