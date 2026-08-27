package com.expensesplit.service;

import com.expensesplit.entity.FcmToken;
import com.expensesplit.entity.User;
import com.expensesplit.repository.FcmTokenRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmTokenServiceTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private UserService userService;

    @InjectMocks
    private FcmTokenService fcmTokenService;

    private User alice;
    private User bob;
    private FirebaseUserPrincipal alicePrincipal;

    @BeforeEach
    void setup() {
        alice = User.builder().id(1L).firebaseUid("alice").name("Alice").email("alice@example.com").build();
        bob = User.builder().id(2L).firebaseUid("bob").name("Bob").email("bob@example.com").build();
        alicePrincipal = new FirebaseUserPrincipal("alice", "alice@example.com", "Alice", null);
    }

    @Test
    void register_savesNewToken() {
        when(userService.getEntityByFirebaseUid("alice")).thenReturn(alice);
        when(fcmTokenRepository.findByToken("tok")).thenReturn(Optional.empty());

        fcmTokenService.register("tok", alicePrincipal);

        ArgumentCaptor<FcmToken> captor = ArgumentCaptor.forClass(FcmToken.class);
        verify(fcmTokenRepository).save(captor.capture());
        assertEquals("tok", captor.getValue().getToken());
        assertEquals(alice, captor.getValue().getUser());
    }

    @Test
    void register_reassignsExistingTokenToCurrentUser() {
        FcmToken existing = FcmToken.builder().id(9L).user(bob).token("tok").build();
        when(userService.getEntityByFirebaseUid("alice")).thenReturn(alice);
        when(fcmTokenRepository.findByToken("tok")).thenReturn(Optional.of(existing));

        fcmTokenService.register("tok", alicePrincipal);

        verify(fcmTokenRepository, never()).save(any());
        assertEquals(alice, existing.getUser());
    }

    @Test
    void unregister_deletesForCurrentUser() {
        when(userService.getEntityByFirebaseUid("alice")).thenReturn(alice);

        fcmTokenService.unregister("tok", alicePrincipal);

        verify(fcmTokenRepository).deleteByTokenAndUserId("tok", 1L);
    }
}
