package com.expensesplit.service;

import com.expensesplit.dto.UserDto;
import com.expensesplit.entity.User;
import com.expensesplit.repository.UserRepository;
import com.expensesplit.security.FirebaseUserPrincipal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserDto getOrCreateUser(FirebaseUserPrincipal principal) {
        User user = userRepository.findByFirebaseUid(principal.getUid())
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setFirebaseUid(principal.getUid());
                    newUser.setEmail(principal.getEmail() != null ? principal.getEmail() : principal.getUid() + "@example.com");
                    newUser.setName(resolveName(principal));
                    newUser.setAvatarUrl(principal.getAvatarUrl());
                    return userRepository.save(newUser);
                });
        
        // Update user name/avatar if they changed in Firebase (sync)
        boolean updated = false;
        String resolvedName = resolveName(principal);
        if (!resolvedName.equals(user.getName())
                && (hasDisplayName(principal)
                        || isGeneratedUidUsername(user.getName(), user.getFirebaseUid()))) {
            user.setName(resolvedName);
            updated = true;
        }
        if (principal.getAvatarUrl() != null && !principal.getAvatarUrl().equals(user.getAvatarUrl())) {
            user.setAvatarUrl(principal.getAvatarUrl());
            updated = true;
        }
        if (updated) {
            user = userRepository.save(user);
        }

        return convertToDto(user);
    }

    static String resolveName(FirebaseUserPrincipal principal) {
        if (hasDisplayName(principal)) {
            return principal.getName().trim();
        }
        return nameFromEmail(principal.getEmail());
    }

    static boolean hasDisplayName(FirebaseUserPrincipal principal) {
        return principal.getName() != null && !principal.getName().isBlank();
    }

    static String nameFromEmail(String email) {
        if (email == null || email.isBlank()) {
            return "Someone";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "Someone";
        }
        String local = email.substring(0, at).trim();
        if (local.isBlank()) {
            return "Someone";
        }
        if (local.length() == 1) {
            return local.toUpperCase(Locale.ROOT);
        }
        return local.substring(0, 1).toUpperCase(Locale.ROOT) + local.substring(1);
    }

    static boolean isGeneratedUidUsername(String name, String firebaseUid) {
        return name != null && firebaseUid != null && name.equals("User " + firebaseUid);
    }

    public User getEntityById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new com.expensesplit.exception.ResourceNotFoundException("User not found with id: " + id));
    }

    public User getEntityByFirebaseUid(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid)
                .orElseThrow(() -> new com.expensesplit.exception.ResourceNotFoundException("User not found with firebaseUid: " + firebaseUid));
    }

    public UserDto convertToDto(User user) {
        if (user == null) return null;
        return UserDto.builder()
                .id(user.getId())
                .firebaseUid(user.getFirebaseUid())
                .name(user.getName())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .build();
    }
}
