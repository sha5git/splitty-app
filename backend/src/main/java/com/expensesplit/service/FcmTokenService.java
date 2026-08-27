package com.expensesplit.service;

import com.expensesplit.entity.FcmToken;
import com.expensesplit.entity.User;
import com.expensesplit.repository.FcmTokenRepository;
import com.expensesplit.security.FirebaseUserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class FcmTokenService {

    private final FcmTokenRepository fcmTokenRepository;
    private final UserService userService;

    public FcmTokenService(FcmTokenRepository fcmTokenRepository, UserService userService) {
        this.fcmTokenRepository = fcmTokenRepository;
        this.userService = userService;
    }

    @Transactional
    public void register(String token, FirebaseUserPrincipal principal) {
        User user = userService.getEntityByFirebaseUid(principal.getUid());
        fcmTokenRepository.findByToken(token).ifPresentOrElse(existing -> {
            existing.setUser(user);
            existing.setCreatedAt(LocalDateTime.now());
        }, () -> fcmTokenRepository.save(FcmToken.builder()
                .user(user)
                .token(token)
                .build()));
    }

    @Transactional
    public void unregister(String token, FirebaseUserPrincipal principal) {
        User user = userService.getEntityByFirebaseUid(principal.getUid());
        fcmTokenRepository.deleteByTokenAndUserId(token, user.getId());
    }
}
