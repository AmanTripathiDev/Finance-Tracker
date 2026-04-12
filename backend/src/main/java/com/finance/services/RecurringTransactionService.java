package com.finance.services;

import com.finance.dto.RecurringDtos.RecurringTransactionRequest;
import com.finance.dto.RecurringDtos.RecurringTransactionResponse;
import com.finance.entities.RecurringFrequency;
import com.finance.entities.RecurringTransaction;
import com.finance.entities.TransactionType;
import com.finance.exception.BadRequestException;
import com.finance.exception.NotFoundException;
import com.finance.repositories.RecurringTransactionRepository;
import com.finance.repositories.UserRepository;
import com.finance.security.CurrentUserService;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RecurringTransactionService {

    private final RecurringTransactionRepository recurringTransactionRepository;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final TransactionService transactionService;
    private final AnalyticsCacheService analyticsCacheService;

    @Transactional(readOnly = true)
    public List<RecurringTransactionResponse> getAll() {
        return recurringTransactionRepository.findByUserIdOrderByNextRunDateAsc(currentUserService.getCurrentUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public RecurringTransactionResponse create(RecurringTransactionRequest request) {
        validateType(request.type());
        validateDateRange(request);
        UUID userId = currentUserService.getCurrentUserId();
        RecurringTransaction recurring = new RecurringTransaction();
        recurring.setUser(userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found")));
        map(recurring, request, userId);
        return toResponse(recurringTransactionRepository.save(recurring));
    }

    @Transactional
    public RecurringTransactionResponse update(UUID id, RecurringTransactionRequest request) {
        validateType(request.type());
        validateDateRange(request);
        UUID userId = currentUserService.getCurrentUserId();
        RecurringTransaction recurring = getRecurring(id, userId);
        map(recurring, request, userId);
        return toResponse(recurringTransactionRepository.save(recurring));
    }

    @Transactional
    public void delete(UUID id) {
        recurringTransactionRepository.delete(getRecurring(id, currentUserService.getCurrentUserId()));
    }

    @Transactional
    public void processDueTransactions() {
        LocalDate today = LocalDate.now();
        recurringTransactionRepository.findByAutoCreateTransactionTrueAndNextRunDateLessThanEqual(today)
                .forEach(this::processRecurringTransaction);
    }

    @Transactional(readOnly = true)
    public RecurringTransaction getRecurring(UUID id, UUID userId) {
        return recurringTransactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Recurring transaction not found"));
    }

    private void processRecurringTransaction(RecurringTransaction recurring) {
        if (recurring.getEndDate() != null && recurring.getNextRunDate().isAfter(recurring.getEndDate())) {
            recurring.setAutoCreateTransaction(false);
            recurringTransactionRepository.save(recurring);
            return;
        }

        transactionService.createAutomatedTransaction(
                recurring.getUser(),
                recurring.getAccount(),
                recurring.getCategory(),
                recurring.getType(),
                recurring.getAmount(),
                recurring.getNextRunDate(),
                "Auto-created from recurring transaction: " + recurring.getTitle()
        );
        analyticsCacheService.evictUserAnalytics(recurring.getUser().getId());

        recurring.setNextRunDate(nextDate(recurring.getNextRunDate(), recurring.getFrequency()));
        if (recurring.getEndDate() != null && recurring.getNextRunDate().isAfter(recurring.getEndDate())) {
            recurring.setAutoCreateTransaction(false);
        }
        recurringTransactionRepository.save(recurring);
    }

    private void map(RecurringTransaction recurring, RecurringTransactionRequest request, UUID userId) {
        recurring.setTitle(request.title());
        recurring.setType(request.type());
        recurring.setAmount(request.amount());
        recurring.setCategory(request.categoryId() == null ? null : categoryService.getCategory(request.categoryId(), userId));
        recurring.setAccount(accountService.getAccount(request.accountId(), userId));
        recurring.setFrequency(request.frequency());
        recurring.setStartDate(request.startDate());
        recurring.setEndDate(request.endDate());
        recurring.setNextRunDate(request.nextRunDate());
        recurring.setAutoCreateTransaction(request.autoCreateTransaction());
    }

    private RecurringTransactionResponse toResponse(RecurringTransaction recurring) {
        return new RecurringTransactionResponse(
                recurring.getId(),
                recurring.getTitle(),
                recurring.getType(),
                recurring.getAmount(),
                recurring.getCategory() == null ? null : recurring.getCategory().getId(),
                recurring.getCategory() == null ? null : recurring.getCategory().getName(),
                recurring.getAccount().getId(),
                recurring.getAccount().getName(),
                recurring.getFrequency(),
                recurring.getStartDate(),
                recurring.getEndDate(),
                recurring.getNextRunDate(),
                recurring.isAutoCreateTransaction()
        );
    }

    private LocalDate nextDate(LocalDate base, RecurringFrequency frequency) {
        return switch (frequency) {
            case DAILY -> base.plusDays(1);
            case WEEKLY -> base.plusWeeks(1);
            case MONTHLY -> base.plusMonths(1);
            case YEARLY -> base.plusYears(1);
        };
    }

    private void validateType(TransactionType type) {
        if (type == TransactionType.TRANSFER_IN || type == TransactionType.TRANSFER_OUT) {
            throw new BadRequestException("Recurring transfers are not supported");
        }
    }

    private void validateDateRange(RecurringTransactionRequest request) {
        if (request.nextRunDate().isBefore(request.startDate())) {
            throw new BadRequestException("Next run date must be on or after start date");
        }
        if (request.endDate() != null && request.nextRunDate().isAfter(request.endDate())) {
            throw new BadRequestException("Next run date must be on or before end date");
        }
    }
}
