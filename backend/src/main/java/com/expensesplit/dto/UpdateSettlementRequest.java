package com.expensesplit.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSettlementRequest {
    @NotNull(message = "Payer (fromUserId) cannot be null")
    private Long fromUserId;

    @NotNull(message = "Receiver (toUserId) cannot be null")
    private Long toUserId;

    @NotNull(message = "Amount cannot be null")
    @DecimalMin(value = "0.01", message = "Amount must be greater than 0")
    private BigDecimal amount;
}
