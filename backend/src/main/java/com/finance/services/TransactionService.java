package com.finance.services;

import com.finance.dto.TransactionDtos.TransactionRequest;
import com.finance.dto.TransactionDtos.TransactionResponse;
import com.finance.entities.Category;
import com.finance.entities.Transaction;
import com.finance.entities.TransactionType;
import com.finance.entities.User;
import com.finance.exception.BadRequestException;
import com.finance.exception.NotFoundException;
import com.finance.repositories.TransactionRepository;
import com.finance.repositories.UserRepository;
import com.finance.security.CurrentUserService;
import jakarta.persistence.criteria.Predicate;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AccountService accountService;
    private final CategoryService categoryService;
    private final LedgerService ledgerService;
    private final AnalyticsCacheService analyticsCacheService;

    @Transactional(readOnly = true)
    public Page<TransactionResponse> search(String search,
                                            UUID categoryId,
                                            UUID accountId,
                                            TransactionType type,
                                            LocalDate startDate,
                                            LocalDate endDate,
                                            int page,
                                            int size) {
        UUID userId = currentUserService.getCurrentUserId();
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("createdAt")));
        Specification<Transaction> specification = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (accountId != null) {
                predicates.add(cb.equal(root.get("account").get("id"), accountId));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), endDate));
            }
            if (search != null && !search.isBlank()) {
                String pattern = "%" + search.toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(cb.coalesce(root.get("merchant"), "")), pattern),
                        cb.like(cb.lower(cb.coalesce(root.get("note"), "")), pattern)
                ));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };

        return transactionRepository.findAll(specification, pageable).map(this::toResponse);
    }

    @Transactional
    @CacheEvict(cacheNames = {"reports", "dashboard"}, allEntries = true)
    public TransactionResponse create(TransactionRequest request) {
        validateRequestPayload(request);
        UUID userId = currentUserService.getCurrentUserId();
        log.info(
                "Creating transaction for userId={}, accountId={}, categoryId={}, type={}, amount={}, transactionDate={}",
                userId,
                request.accountId(),
                request.categoryId(),
                request.type(),
                request.amount(),
                request.transactionDate()
        );
        validateManualTransactionType(request.type());
        try {
            User user = getUser(userId);
            var account = accountService.getAccount(request.accountId(), userId);
            Category category = request.categoryId() == null ? null : categoryService.getCategory(request.categoryId(), userId);

            Transaction transaction = ledgerService.createTransaction(
                    user,
                    account,
                    category,
                    request.type(),
                    request.amount(),
                    request.transactionDate(),
                    request.merchant(),
                    request.note(),
                    request.paymentMethod(),
                    null
            );
            analyticsCacheService.evictUserAnalytics(userId);
            log.info("Transaction created successfully with id={} for userId={}", transaction.getId(), userId);
            return toResponse(transaction);
        } catch (RuntimeException exception) {
            log.error(
                    "Failed to create transaction for userId={}, accountId={}, categoryId={}, type={}, amount={}, transactionDate={}",
                    userId,
                    request.accountId(),
                    request.categoryId(),
                    request.type(),
                    request.amount(),
                    request.transactionDate(),
                    exception
            );
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID id) {
        return toResponse(getTransaction(id, currentUserService.getCurrentUserId()));
    }

    @Transactional
    @CacheEvict(cacheNames = {"reports", "dashboard"}, allEntries = true)
    public TransactionResponse update(UUID id, TransactionRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        validateManualTransactionType(request.type());
        Transaction existing = getTransaction(id, userId);
        if (existing.getType() == TransactionType.TRANSFER_IN || existing.getType() == TransactionType.TRANSFER_OUT) {
            throw new BadRequestException("Transfer transactions cannot be edited from this endpoint");
        }

        ledgerService.reverseTransaction(existing);

        existing.setAccount(accountService.getAccount(request.accountId(), userId));
        existing.setCategory(request.categoryId() == null ? null : categoryService.getCategory(request.categoryId(), userId));
        existing.setType(request.type());
        existing.setAmount(request.amount());
        existing.setTransactionDate(request.transactionDate());
        existing.setMerchant(request.merchant());
        existing.setNote(request.note());
        existing.setPaymentMethod(request.paymentMethod());

        ledgerService.applyEffect(existing.getAccount(), existing.getType(), existing.getAmount());
        TransactionResponse response = toResponse(transactionRepository.save(existing));
        analyticsCacheService.evictUserAnalytics(userId);
        return response;
    }

    @Transactional
    @CacheEvict(cacheNames = {"reports", "dashboard"}, allEntries = true)
    public void delete(UUID id) {
        Transaction transaction = getTransaction(id, currentUserService.getCurrentUserId());
        if (transaction.getType() == TransactionType.TRANSFER_IN || transaction.getType() == TransactionType.TRANSFER_OUT) {
            throw new BadRequestException("Transfer transactions cannot be deleted from this endpoint");
        }
        ledgerService.reverseTransaction(transaction);
        transactionRepository.delete(transaction);
        analyticsCacheService.evictUserAnalytics(currentUserService.getCurrentUserId());
    }

    @Transactional(readOnly = true)
    public Transaction getTransaction(UUID id, UUID userId) {
        return transactionRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Transaction not found"));
    }

    @Transactional
    public Transaction createAutomatedTransaction(User user,
                                                  com.finance.entities.Account account,
                                                  Category category,
                                                  TransactionType type,
                                                  BigDecimal amount,
                                                  LocalDate date,
                                                  String note) {
        Transaction transaction = ledgerService.createTransaction(user, account, category, type, amount, date, null, note, "AUTO", null);
        analyticsCacheService.evictUserAnalytics(user.getId());
        return transaction;
    }

    private void validateManualTransactionType(TransactionType type) {
        if (type == TransactionType.TRANSFER_IN || type == TransactionType.TRANSFER_OUT) {
            throw new BadRequestException("Use the transfer endpoint for account transfers");
        }
    }

    private void validateRequestPayload(TransactionRequest request) {
        if (request == null) {
            throw new BadRequestException("Transaction request body is required");
        }
        if (request.accountId() == null) {
            throw new BadRequestException("accountId is required");
        }
        if (request.type() == null) {
            throw new BadRequestException("type is required");
        }
        if (request.amount() == null) {
            throw new BadRequestException("amount is required");
        }
        if (request.amount().signum() <= 0) {
            throw new BadRequestException("amount must be greater than zero");
        }
        if (request.transactionDate() == null) {
            throw new BadRequestException("transactionDate is required");
        }
    }

    private User getUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private TransactionResponse toResponse(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getAccount().getName(),
                transaction.getCategory() == null ? null : transaction.getCategory().getId(),
                transaction.getCategory() == null ? null : transaction.getCategory().getName(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getTransactionDate(),
                transaction.getMerchant(),
                transaction.getNote(),
                transaction.getPaymentMethod(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}
