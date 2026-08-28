package com.expensesplit.service;

import com.expensesplit.dto.UserDto;
import com.expensesplit.entity.User;
import com.expensesplit.repository.UserRepository;
import com.expensesplit.security.FirebaseUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @BeforeEach
    void stubSave() {
        lenient().when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            if (user.getId() == null) {
                user.setId(1L);
            }
            return user;
        });
    }

    @Test
    void create_usesFirebaseDisplayName() {
        when(userRepository.findByFirebaseUid("uid-1")).thenReturn(Optional.empty());

        UserDto dto = userService.getOrCreateUser(
                new FirebaseUserPrincipal("uid-1", "shashank@example.com", "Shashank", null));

        assertEquals("Shashank", dto.getName());
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("Shashank", captor.getValue().getName());
        assertFalse(captor.getValue().getName().startsWith("User "));
    }

    @Test
    void create_fallsBackToCapitalizedEmailLocalPartWhenNameMissing() {
        when(userRepository.findByFirebaseUid("uid-1")).thenReturn(Optional.empty());

        UserDto dto = userService.getOrCreateUser(
                new FirebaseUserPrincipal("uid-1", "shashank@shashankdev.com", null, null));

        assertEquals("Shashank", dto.getName());
    }

    @Test
    void create_fallsBackToSomeoneWhenEmailMissing() {
        when(userRepository.findByFirebaseUid("uid-1")).thenReturn(Optional.empty());

        UserDto dto = userService.getOrCreateUser(
                new FirebaseUserPrincipal("uid-1", null, null, null));

        assertEquals("Someone", dto.getName());
    }

    @Test
    void create_neverUsesUidUsername() {
        when(userRepository.findByFirebaseUid("wxOprGFfupgnviT3K2gEiadnL5x2")).thenReturn(Optional.empty());

        UserDto dto = userService.getOrCreateUser(new FirebaseUserPrincipal(
                "wxOprGFfupgnviT3K2gEiadnL5x2",
                "shashank@shashankdev.com",
                null,
                null));

        assertEquals("Shashank", dto.getName());
        assertFalse(dto.getName().contains("wxOprGFfupgnviT3K2gEiadnL5x2"));
    }

    @Test
    void update_repairsGeneratedUidUsernameWhenTokenHasName() {
        User existing = User.builder()
                .id(4L)
                .firebaseUid("wxOprGFfupgnviT3K2gEiadnL5x2")
                .name("User wxOprGFfupgnviT3K2gEiadnL5x2")
                .email("shashank@shashankdev.com")
                .build();
        when(userRepository.findByFirebaseUid("wxOprGFfupgnviT3K2gEiadnL5x2")).thenReturn(Optional.of(existing));

        UserDto dto = userService.getOrCreateUser(new FirebaseUserPrincipal(
                "wxOprGFfupgnviT3K2gEiadnL5x2",
                "shashank@shashankdev.com",
                "Shashank",
                null));

        assertEquals("Shashank", dto.getName());
        verify(userRepository).save(existing);
    }

    @Test
    void update_repairsGeneratedUidUsernameFromEmailWhenTokenHasNoName() {
        User existing = User.builder()
                .id(4L)
                .firebaseUid("wxOprGFfupgnviT3K2gEiadnL5x2")
                .name("User wxOprGFfupgnviT3K2gEiadnL5x2")
                .email("shashank@shashankdev.com")
                .build();
        when(userRepository.findByFirebaseUid("wxOprGFfupgnviT3K2gEiadnL5x2")).thenReturn(Optional.of(existing));

        UserDto dto = userService.getOrCreateUser(new FirebaseUserPrincipal(
                "wxOprGFfupgnviT3K2gEiadnL5x2",
                "shashank@shashankdev.com",
                null,
                null));

        assertEquals("Shashank", dto.getName());
        verify(userRepository).save(existing);
    }

    @Test
    void update_doesNotOverwriteRealNameWhenTokenHasNoName() {
        User existing = User.builder()
                .id(4L)
                .firebaseUid("uid-1")
                .name("Shashank")
                .email("shashank@shashankdev.com")
                .build();
        when(userRepository.findByFirebaseUid("uid-1")).thenReturn(Optional.of(existing));

        UserDto dto = userService.getOrCreateUser(
                new FirebaseUserPrincipal("uid-1", "shashank@shashankdev.com", null, null));

        assertEquals("Shashank", dto.getName());
        verify(userRepository, never()).save(any());
    }
}
