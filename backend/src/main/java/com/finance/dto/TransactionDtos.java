package com.finance.dto;

import com.finance.entities.TransactionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

public class TransactionDtos {

    public record TransactionRequest(
            @Schema(example = "4e6d9a8e-2a21-4e3a-9f73-3f8c4a1d7f77")
            @NotNull UUID accountId,
            @Schema(example = "b2ef44ad-96a8-4f2e-9ca7-5d6c9a2a8d3a")
            UUID categoryId,
            @Schema(example = "EXPENSE")
            @NotNull TransactionType type,
            @Schema(example = "45.90")
            @NotNull @DecimalMin(value = "0.01") BigDecimal amount,
            @Schema(example = "2026-04-12")
            @NotNull LocalDate transactionDate,
            @Schema(example = "Starbucks")
            String merchant,
            @Schema(example = "Coffee with client")
            String note,
            @Schema(example = "Card")
            String paymentMethod
    ) {
    }

    public record TransactionResponse(
            @Schema(example = "7c9f0a4b-7f6f-4b65-a5cf-8a9ad8f4d0b1")
            UUID id,
            UUID accountId,
            String accountName,
            UUID categoryId,
            String categoryName,
            @Schema(example = "EXPENSE")
            TransactionType type,
            @Schema(example = "45.90")
            BigDecimal amount,
            @Schema(example = "2026-04-12")
            LocalDate transactionDate,
            String merchant,
            String note,
            String paymentMethod,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt
    ) {
    }
}
