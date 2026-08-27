package com.expensesplit.service;

import com.expensesplit.dto.GroupChangeEvent;
import com.expensesplit.repository.GroupMemberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupEventPublisherTest {

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private FcmPushService fcmPushService;

    @Test
    void publishAfterCommit_withNoSubscribers_doesNotThrow() {
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(Collections.emptyList());
        GroupEventPublisher publisher =
                new GroupEventPublisher(groupMemberRepository, new ObjectMapper(), fcmPushService);

        publisher.publishAfterCommit(GroupChangeEvent.builder()
                .groupId(10L)
                .type("EXPENSE_CREATED")
                .entityId(1L)
                .actorUserId(1L)
                .build());

        verify(fcmPushService).notifyMembers(any(GroupChangeEvent.class), eq(Collections.emptySet()));
    }
}
