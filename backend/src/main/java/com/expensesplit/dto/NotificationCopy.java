package com.expensesplit.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class NotificationCopy {
    private String title;
    private String body;
}
