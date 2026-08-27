package com.expensesplit.service;

import com.expensesplit.dto.GroupChangeEvent;
import com.expensesplit.dto.NotificationCopy;
import com.expensesplit.entity.Expense;
import com.expensesplit.entity.Group;
import com.expensesplit.entity.Settlement;
import com.expensesplit.entity.User;
import com.expensesplit.repository.ExpenseRepository;
import com.expensesplit.repository.GroupRepository;
import com.expensesplit.repository.SettlementRepository;
import com.expensesplit.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationCopyServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @InjectMocks
    private NotificationCopyService notificationCopyService;

    private User alice;
    private User bob;
    private Group group;

    @BeforeEach
    void setup() {
        alice = User.builder().id(1L).name("Alice").email("a@example.com").firebaseUid("a").build();
        bob = User.builder().id(2L).name("Bob").email("b@example.com").firebaseUid("b").build();
        group = Group.builder().id(10L).name("Weekend in Goa").createdBy(alice).build();
    }

    @Test
    void expenseCreated_usesYouWhenRecipientIsActor() {
        Expense expense = Expense.builder()
                .id(100L)
                .group(group)
                .description("Lunch")
                .amount(new BigDecimal("450"))
                .build();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense));

        Optional<NotificationCopy> copy = notificationCopyService.compose(
                GroupChangeEvent.builder()
                        .groupId(10L)
                        .type("EXPENSE_CREATED")
                        .entityId(100L)
                        .actorUserId(1L)
                        .build(),
                1L);

        assertTrue(copy.isPresent());
        assertEquals("Weekend in Goa", copy.get().getTitle());
        assertEquals("You added Lunch · ₹450", copy.get().getBody());
    }

    @Test
    void expenseCreated_usesActorNameForOtherRecipient() {
        Expense expense = Expense.builder()
                .id(100L)
                .group(group)
                .description("Lunch")
                .amount(new BigDecimal("450"))
                .build();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense));

        Optional<NotificationCopy> copy = notificationCopyService.compose(
                GroupChangeEvent.builder()
                        .groupId(10L)
                        .type("EXPENSE_CREATED")
                        .entityId(100L)
                        .actorUserId(1L)
                        .build(),
                2L);

        assertEquals("Alice added Lunch · ₹450", copy.get().getBody());
    }

    @Test
    void expenseDeleted_genericBodyWhenRowIsGone() {
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(userRepository.findById(1L)).thenReturn(Optional.of(alice));

        Optional<NotificationCopy> copy = notificationCopyService.compose(
                GroupChangeEvent.builder()
                        .groupId(10L)
                        .type("EXPENSE_DELETED")
                        .entityId(100L)
                        .actorUserId(1L)
                        .build(),
                2L);

        assertEquals("Alice removed an expense", copy.get().getBody());
    }

    @Test
    void settlementCreated_youPaidBob() {
        Settlement settlement = Settlement.builder()
                .id(5L)
                .fromUser(alice)
                .toUser(bob)
                .amount(new BigDecimal("500"))
                .build();
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(settlementRepository.findById(5L)).thenReturn(Optional.of(settlement));

        Optional<NotificationCopy> copy = notificationCopyService.compose(
                GroupChangeEvent.builder()
                        .groupId(10L)
                        .type("SETTLEMENT_CREATED")
                        .entityId(5L)
                        .actorUserId(1L)
                        .build(),
                1L);

        assertEquals("You paid Bob ₹500", copy.get().getBody());
    }

    @Test
    void groupCreated_skippedForActor() {
        Optional<NotificationCopy> copy = notificationCopyService.compose(
                GroupChangeEvent.builder()
                        .groupId(10L)
                        .type("GROUP_CREATED")
                        .actorUserId(1L)
                        .build(),
                1L);

        assertTrue(copy.isEmpty());
    }

    @Test
    void formatInr_usesIndianGrouping() {
        assertEquals("₹1,240", NotificationCopyService.formatInr(new BigDecimal("1240")));
    }
}
