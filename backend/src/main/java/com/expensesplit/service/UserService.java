package com.expensesplit.service;

import com.expensesplit.dto.UserDto;
import com.expensesplit.entity.User;
import com.expensesplit.repository.UserRepository;
import com.expensesplit.security.FirebaseUserPrincipal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
                    newUser.setName(principal.getName() != null ? principal.getName() : "User " + principal.getUid());
                    newUser.setAvatarUrl(principal.getAvatarUrl());
                    return userRepository.save(newUser);
                });
        
        // Update user name/avatar if they changed in Firebase (sync)
        boolean updated = false;
        if (principal.getName() != null && !principal.getName().equals(user.getName())) {
            user.setName(principal.getName());
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
