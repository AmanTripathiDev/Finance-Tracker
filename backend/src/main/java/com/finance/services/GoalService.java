package com.finance.services;

import com.finance.dto.GoalDtos.GoalContributionRequest;
import com.finance.dto.GoalDtos.GoalRequest;
import com.finance.dto.GoalDtos.GoalResponse;
import com.finance.entities.Goal;
import com.finance.entities.GoalStatus;
import com.finance.entities.TransactionType;
import com.finance.exception.BadRequestException;
import com.finance.exception.NotFoundException;
import com.finance.repositories.GoalRepository;
import com.finance.repositories.UserRepository;
import com.finance.security.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GoalService {

    private final GoalRepository goalRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final AccountService accountService;
    private final LedgerService ledgerService;
    private final AnalyticsCacheService analyticsCacheService;

    @Transactional(readOnly = true)
    public List<GoalResponse> getAll() {
        return goalRepository.findByUserIdOrderByTargetDateAsc(currentUserService.getCurrentUserId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public GoalResponse create(GoalRequest request) {
        Goal goal = new Goal();
        goal.setUser(userRepository.findById(currentUserService.getCurrentUserId()).orElseThrow(() -> new NotFoundException("User not found")));
        goal.setName(request.name());
        goal.setTargetAmount(request.targetAmount());
        goal.setCurrentAmount(BigDecimal.ZERO);
        goal.setTargetDate(request.targetDate());
        goal.setStatus(request.status() == null ? GoalStatus.ACTIVE : request.status());
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse update(UUID id, GoalRequest request) {
        Goal goal = getGoal(id, currentUserService.getCurrentUserId());
        goal.setName(request.name());
        goal.setTargetAmount(request.targetAmount());
        goal.setTargetDate(request.targetDate());
        goal.setStatus(request.status() == null ? goal.getStatus() : request.status());
        recalculateStatus(goal);
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse contribute(UUID id, GoalContributionRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        Goal goal = getGoal(id, userId);
        var account = accountService.getAccount(request.accountId(), userId);
        if (account.getCurrentBalance().compareTo(request.amount()) < 0) {
            throw new BadRequestException("Insufficient account balance");
        }

        goal.setCurrentAmount(goal.getCurrentAmount().add(request.amount()));
        recalculateStatus(goal);
        ledgerService.createTransaction(
                goal.getUser(),
                account,
                null,
                TransactionType.TRANSFER_OUT,
                request.amount(),
                LocalDate.now(),
                goal.getName(),
                "Contribution to goal: " + goal.getName(),
                "GOAL",
                UUID.randomUUID()
        );
        analyticsCacheService.evictUserAnalytics(userId);
        return toResponse(goalRepository.save(goal));
    }

    @Transactional
    public GoalResponse withdraw(UUID id, GoalContributionRequest request) {
        UUID userId = currentUserService.getCurrentUserId();
        Goal goal = getGoal(id, userId);
        if (goal.getCurrentAmount().compareTo(request.amount()) < 0) {
            throw new BadRequestException("Insufficient goal balance");
        }
        var account = accountService.getAccount(request.accountId(), userId);
        goal.setCurrentAmount(goal.getCurrentAmount().subtract(request.amount()));
        recalculateStatus(goal);
        ledgerService.createTransaction(
                goal.getUser(),
                account,
                null,
                TransactionType.TRANSFER_IN,
                request.amount(),
                LocalDate.now(),
                goal.getName(),
                "Withdrawal from goal: " + goal.getName(),
                "GOAL",
                UUID.randomUUID()
        );
        analyticsCacheService.evictUserAnalytics(userId);
        return toResponse(goalRepository.save(goal));
    }

    @Transactional(readOnly = true)
    public Goal getGoal(UUID id, UUID userId) {
        return goalRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Goal not found"));
    }

    private void recalculateStatus(Goal goal) {
        if (goal.getCurrentAmount().compareTo(goal.getTargetAmount()) >= 0) {
            goal.setStatus(GoalStatus.COMPLETED);
        } else if (goal.getStatus() == GoalStatus.COMPLETED) {
            goal.setStatus(GoalStatus.ACTIVE);
        }
    }

    private GoalResponse toResponse(Goal goal) {
        BigDecimal progress = goal.getTargetAmount().compareTo(BigDecimal.ZERO) == 0
                ? BigDecimal.ZERO
                : goal.getCurrentAmount().multiply(BigDecimal.valueOf(100))
                .divide(goal.getTargetAmount(), 2, RoundingMode.HALF_UP);
        return new GoalResponse(
                goal.getId(),
                goal.getName(),
                goal.getTargetAmount(),
                goal.getCurrentAmount(),
                goal.getTargetDate(),
                goal.getStatus(),
                progress
        );
    }
}
