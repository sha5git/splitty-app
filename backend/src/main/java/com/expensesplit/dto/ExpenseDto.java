package com.expensesplit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExpenseDto {
    private Long id;
    private Long groupId;
    private UserDto paidBy;
    private BigDecimal amount;
    private String description;
    private LocalDateTime date;
    private String splitType;
    private List<ExpenseSplitDto> splits;
}
