package com.expensesplit.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupChangeEvent {
    private Long groupId;
    private String type;
    private Long entityId;
    private Long actorUserId;
}
