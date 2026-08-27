package com.expensesplit.service;

import com.expensesplit.dto.GroupChangeEvent;
import com.expensesplit.entity.GroupMember;
import com.expensesplit.repository.GroupMemberRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class GroupEventPublisher {

    public static final String EVENT_NAME = "group-change";

    private static final Logger log = LoggerFactory.getLogger(GroupEventPublisher.class);
    private static final long HEARTBEAT_MS = 15_000L;

    private final GroupMemberRepository groupMemberRepository;
    private final ObjectMapper objectMapper;
    private final FcmPushService fcmPushService;
    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUserId =
            new ConcurrentHashMap<>();

    public GroupEventPublisher(
            GroupMemberRepository groupMemberRepository,
            ObjectMapper objectMapper,
            FcmPushService fcmPushService) {
        this.groupMemberRepository = groupMemberRepository;
        this.objectMapper = objectMapper;
        this.fcmPushService = fcmPushService;
    }

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(0L);
        emittersByUserId.computeIfAbsent(userId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        Runnable remove = () -> removeEmitter(userId, emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(error -> remove.run());

        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove.run();
        }

        return emitter;
    }

    public void publishAfterCommit(GroupChangeEvent event, Long... extraUserIds) {
        Long[] extras = extraUserIds == null ? new Long[0] : extraUserIds.clone();
        Runnable send = () -> fanOut(event, extras);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send.run();
                }
            });
        } else {
            send.run();
        }
    }

    @Scheduled(fixedRate = HEARTBEAT_MS)
    void heartbeat() {
        emittersByUserId.forEach((userId, emitters) -> {
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().comment("keepalive"));
                } catch (IOException e) {
                    removeEmitter(userId, emitter);
                }
            }
        });
    }

    private void fanOut(GroupChangeEvent event, Long[] extraUserIds) {
        Set<Long> userIds = new HashSet<>();
        if (event.getGroupId() != null) {
            for (GroupMember member : groupMemberRepository.findByGroupId(event.getGroupId())) {
                userIds.add(member.getId().getUserId());
            }
        }
        userIds.addAll(Arrays.asList(extraUserIds));
        userIds.remove(null);

        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize group change event", e);
            return;
        }

        for (Long userId : userIds) {
            List<SseEmitter> emitters = emittersByUserId.get(userId);
            if (emitters == null) {
                continue;
            }
            for (SseEmitter emitter : emitters) {
                try {
                    emitter.send(SseEmitter.event().name(EVENT_NAME).data(json));
                } catch (IOException e) {
                    removeEmitter(userId, emitter);
                }
            }
        }

        fcmPushService.notifyMembers(event, userIds);
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByUserId.remove(userId, emitters);
        }
    }
}
