package com.expensesplit.controller;

import com.expensesplit.dto.UserDto;
import com.expensesplit.security.FirebaseUserPrincipal;
import com.expensesplit.service.UserService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public UserDto getMe(@AuthenticationPrincipal FirebaseUserPrincipal principal) {
        if (principal == null) {
            throw new com.expensesplit.exception.BadRequestException("Authentication principal is missing");
        }
        return userService.getOrCreateUser(principal);
    }
}
