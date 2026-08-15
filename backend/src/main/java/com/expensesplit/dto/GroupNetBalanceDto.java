package com.expensesplit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Represents the current user's net balance within a group.
 *
 * <p>A positive {@code netBalance} means the user is owed that amount by the group.
 * A negative {@code netBalance} means the user owes that amount to the group.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupNetBalanceDto {

    /** The group this balance summary belongs to. */
    private Long groupId;

    /** The user whose net balance is summarised. */
    private UserDto user;

    /**
     * Net balance in the group's currency.
     * <ul>
     *   <li>Positive (+): you are owed this amount.</li>
     *   <li>Negative (−): you owe this amount.</li>
     *   <li>Zero: you are fully settled up.</li>
     * </ul>
     */
    private BigDecimal netBalance;
}
