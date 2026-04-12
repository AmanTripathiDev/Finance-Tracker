package com.finance.services;

import com.finance.dto.AccountDtos.AccountRequest;
import com.finance.dto.AccountDtos.AccountResponse;
import com.finance.dto.AccountDtos.TransferRequest;
import com.finance.entities.Account;
import com.finance.entities.TransactionType;
import com.finance.entities.User;
import com.finance.exception.BadRequestException;
import com.finance.exception.NotFoundException;
import com.finance.repositories.AccountRepository;
import com.finance.repositories.UserRepository;
import com.finance.security.CurrentUserService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final LedgerService ledgerService;
    private final AnalyticsCacheService analyticsCacheService;

    @Transactional(readOnly = true)
    public List<AccountResponse> getAll() {
        return accountRepository.findByUserIdOrderByCreatedAtDesc(currentUserService.getCurrentUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AccountResponse create(AccountRequest request) {
        User user = getUser(currentUserService.getCurrentUserId());
        Account account = new Account();
        account.setUser(user);
        map(account, request);
        account.setCurrentBalance(request.openingBalance());
        return toResponse(accountRepository.save(account));
    }

    @Transactional
    public AccountResponse update(UUID id, AccountRequest request) {
        Account account = getAccount(id, currentUserService.getCurrentUserId());
        account.setName(request.name());
        account.setType(request.type());
        account.setInstitutionName(request.institutionName());
        return toResponse(accountRepository.save(account));
    }

    @Transactional
    public void transfer(TransferRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        if (request.sourceAccountId().equals(request.targetAccountId())) {
            throw new BadRequestException("Source and target accounts must be different");
        }

        Account source = getAccount(request.sourceAccountId(), userId);
        Account target = getAccount(request.targetAccountId(), userId);
        if (source.getCurrentBalance().compareTo(request.amount()) < 0) {
            throw new BadRequestException("Insufficient balance for transfer");
        }

        UUID transferGroupId = UUID.randomUUID();
        User user = getUser(userId);

        ledgerService.createTransaction(
                user,
                source,
                null,
                TransactionType.TRANSFER_OUT,
                request.amount(),
                request.transactionDate(),
                target.getName(),
                request.note() == null ? "Transfer to %s".formatted(target.getName()) : request.note(),
                "TRANSFER",
                transferGroupId
        );

        ledgerService.createTransaction(
                user,
                target,
                null,
                TransactionType.TRANSFER_IN,
                request.amount(),
                request.transactionDate(),
                source.getName(),
                request.note() == null ? "Transfer from %s".formatted(source.getName()) : request.note(),
                "TRANSFER",
                transferGroupId
        );
        analyticsCacheService.evictUserAnalytics(userId);
        log.info("Transferred {} from account {} to {}", request.amount(), source.getId(), target.getId());
    }

    @Transactional(readOnly = true)
    public Account getAccount(UUID id, UUID userId) {
        return accountRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Account not found"));
    }

    private User getUser(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User not found"));
    }

    private void map(Account account, AccountRequest request) {
        account.setName(request.name());
        account.setType(request.type());
        account.setOpeningBalance(request.openingBalance());
        account.setInstitutionName(request.institutionName());
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getType(),
                account.getOpeningBalance(),
                account.getCurrentBalance(),
                account.getInstitutionName(),
                account.getCreatedAt()
        );
    }
}
