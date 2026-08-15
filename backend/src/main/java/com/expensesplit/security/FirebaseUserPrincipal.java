package com.expensesplit.security;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FirebaseUserPrincipal {
    private String uid;
    private String email;
    private String name;
    private String avatarUrl;
}
