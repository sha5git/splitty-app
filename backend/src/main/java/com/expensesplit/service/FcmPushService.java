package com.expensesplit.service;

import com.expensesplit.dto.GroupChangeEvent;
import com.expensesplit.dto.NotificationCopy;
import com.expensesplit.entity.FcmToken;
import com.expensesplit.repository.FcmTokenRepository;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Set;

@Service
public class FcmPushService {

    private static final Logger log = LoggerFactory.getLogger(FcmPushService.class);

    private final ObjectProvider<FirebaseMessaging> firebaseMessaging;
    private final FcmTokenRepository fcmTokenRepository;
    private final NotificationCopyService notificationCopyService;
    private final TransactionTemplate transactionTemplate;

    public FcmPushService(
            ObjectProvider<FirebaseMessaging> firebaseMessaging,
            FcmTokenRepository fcmTokenRepository,
            NotificationCopyService notificationCopyService,
            PlatformTransactionManager transactionManager) {
        this.firebaseMessaging = firebaseMessaging;
        this.fcmTokenRepository = fcmTokenRepository;
        this.notificationCopyService = notificationCopyService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Async
    public void notifyMembers(GroupChangeEvent event, Set<Long> userIds) {
        transactionTemplate.executeWithoutResult(status -> sendToMembers(event, userIds));
    }

    private void sendToMembers(GroupChangeEvent event, Set<Long> userIds) {
        FirebaseMessaging messaging = firebaseMessaging.getIfAvailable();
        if (messaging == null || event == null || userIds == null || userIds.isEmpty()) {
            return;
        }

        Set<Long> recipients = userIds;
        if ("GROUP_CREATED".equals(event.getType())
                && event.getActorUserId() != null
                && userIds.size() == 1
                && userIds.contains(event.getActorUserId())) {
            return;
        }

        List<FcmToken> tokens = fcmTokenRepository.findByUserIdIn(recipients);
        if (tokens.isEmpty()) {
            return;
        }

        for (FcmToken row : tokens) {
            Long recipientId = row.getUser().getId();
            notificationCopyService.compose(event, recipientId).ifPresent(copy ->
                    send(messaging, row, event, copy));
        }
    }

    private void send(FirebaseMessaging messaging, FcmToken row, GroupChangeEvent event, NotificationCopy copy) {
        String token = row.getToken();
        try {
            Message.Builder builder = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(copy.getTitle())
                            .setBody(copy.getBody())
                            .build())
                    .putData("type", event.getType() == null ? "" : event.getType())
                    .putData("url", event.getGroupId() == null ? "/" : "/groups/" + event.getGroupId() + "?tab=expenses");
            if (event.getGroupId() != null) {
                builder.putData("groupId", String.valueOf(event.getGroupId()));
            }
            if (event.getEntityId() != null) {
                builder.putData("entityId", String.valueOf(event.getEntityId()));
            }
            messaging.send(builder.build());
        } catch (FirebaseMessagingException e) {
            if (isInvalidToken(e)) {
                fcmTokenRepository.deleteByToken(token);
            } else {
                log.warn("Failed to send FCM notification: {}", e.getMessagingErrorCode());
            }
        }
    }

    private static boolean isInvalidToken(FirebaseMessagingException e) {
        MessagingErrorCode code = e.getMessagingErrorCode();
        return code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT;
    }
}
