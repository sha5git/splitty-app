package com.expensesplit.service;

import com.expensesplit.dto.GroupChangeEvent;
import com.expensesplit.dto.NotificationCopy;
import com.expensesplit.entity.FcmToken;
import com.expensesplit.entity.User;
import com.expensesplit.repository.FcmTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FcmPushServiceTest {

    @Mock
    private ObjectProvider<FirebaseMessaging> firebaseMessaging;

    @Mock
    private FirebaseMessaging messaging;

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private NotificationCopyService notificationCopyService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private FcmPushService fcmPushService;
    private User bob;
    private FcmToken bobToken;

    @BeforeEach
    void setup() {
        TransactionStatus status = new SimpleTransactionStatus();
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(status);
        fcmPushService = new FcmPushService(
                firebaseMessaging, fcmTokenRepository, notificationCopyService, transactionManager);
        bob = User.builder().id(2L).name("Bob").email("b@example.com").firebaseUid("b").build();
        bobToken = FcmToken.builder().id(1L).user(bob).token("device-token").build();
    }

    @Test
    void notifyMembers_noOpsWhenMessagingBeanMissing() {
        when(firebaseMessaging.getIfAvailable()).thenReturn(null);

        fcmPushService.notifyMembers(GroupChangeEvent.builder()
                .groupId(10L)
                .type("EXPENSE_CREATED")
                .build(), Set.of(2L));

        verify(fcmTokenRepository, never()).findByUserIdIn(any());
    }

    @Test
    void notifyMembers_sendsToStoredTokens() throws Exception {
        GroupChangeEvent event = GroupChangeEvent.builder()
                .groupId(10L)
                .type("EXPENSE_CREATED")
                .entityId(100L)
                .actorUserId(1L)
                .build();
        when(firebaseMessaging.getIfAvailable()).thenReturn(messaging);
        when(fcmTokenRepository.findByUserIdIn(Set.of(2L))).thenReturn(List.of(bobToken));
        when(notificationCopyService.compose(event, 2L))
                .thenReturn(Optional.of(new NotificationCopy("Weekend in Goa", "Alice added Lunch · ₹450")));

        fcmPushService.notifyMembers(event, Set.of(2L));

        verify(messaging).send(any(Message.class));
        verify(fcmTokenRepository, never()).deleteByToken(any());
    }

    @Test
    void notifyMembers_deletesUnregisteredToken() throws Exception {
        GroupChangeEvent event = GroupChangeEvent.builder()
                .groupId(10L)
                .type("EXPENSE_CREATED")
                .entityId(100L)
                .build();
        when(firebaseMessaging.getIfAvailable()).thenReturn(messaging);
        when(fcmTokenRepository.findByUserIdIn(Set.of(2L))).thenReturn(List.of(bobToken));
        when(notificationCopyService.compose(event, 2L))
                .thenReturn(Optional.of(new NotificationCopy("Goa", "Alice added Lunch · ₹450")));

        FirebaseMessagingException exception = org.mockito.Mockito.mock(FirebaseMessagingException.class);
        when(exception.getMessagingErrorCode()).thenReturn(MessagingErrorCode.UNREGISTERED);
        when(messaging.send(any(Message.class))).thenThrow(exception);

        fcmPushService.notifyMembers(event, Set.of(2L));

        verify(fcmTokenRepository).deleteByToken("device-token");
    }

    @Test
    void notifyMembers_skipsGroupCreatedWhenOnlyActor() {
        when(firebaseMessaging.getIfAvailable()).thenReturn(messaging);

        fcmPushService.notifyMembers(GroupChangeEvent.builder()
                .groupId(10L)
                .type("GROUP_CREATED")
                .actorUserId(1L)
                .build(), Set.of(1L));

        verify(fcmTokenRepository, never()).findByUserIdIn(any());
    }
}
