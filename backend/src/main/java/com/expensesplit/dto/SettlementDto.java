package com.expensesplit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettlementDto {
    private Long id;
    private Long groupId;
    private UserDto fromUser;
    private UserDto toUser;
    private BigDecimal amount;
    private LocalDateTime date;
}
