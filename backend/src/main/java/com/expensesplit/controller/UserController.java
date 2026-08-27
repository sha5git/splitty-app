package com.expensesplit.controller;

import com.expensesplit.dto.FcmTokenRequest;
import com.expensesplit.dto.UserDto;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.security.FirebaseUserPrincipal;
import com.expensesplit.service.FcmTokenService;
import com.expensesplit.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final FcmTokenService fcmTokenService;

    public UserController(UserService userService, FcmTokenService fcmTokenService) {
        this.userService = userService;
        this.fcmTokenService = fcmTokenService;
    }

    @GetMapping("/me")
    public UserDto getMe(@AuthenticationPrincipal FirebaseUserPrincipal principal) {
        if (principal == null) {
            throw new BadRequestException("Authentication principal is missing");
        }
        return userService.getOrCreateUser(principal);
    }

    @PostMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void registerFcmToken(
            @Valid @RequestBody FcmTokenRequest request,
            @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        requirePrincipal(principal);
        fcmTokenService.register(request.getToken().trim(), principal);
    }

    @DeleteMapping("/me/fcm-token")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFcmToken(
            @Valid @RequestBody FcmTokenRequest request,
            @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        requirePrincipal(principal);
        fcmTokenService.unregister(request.getToken().trim(), principal);
    }

    private static void requirePrincipal(FirebaseUserPrincipal principal) {
        if (principal == null) {
            throw new BadRequestException("Authentication principal is missing");
        }
    }
}
