package com.expensesplit.service;

import com.expensesplit.dto.ExpenseDto;
import com.expensesplit.dto.UpdateExpenseRequest;
import com.expensesplit.dto.UserDto;
import com.expensesplit.entity.*;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.repository.ExpenseRepository;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.security.FirebaseUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ExpenseServiceTest {

    @InjectMocks
    private ExpenseService expenseService;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private GroupService groupService;

    @Mock
    private UserService userService;

    private User u1;
    private User u2;
    private Group group;
    private FirebaseUserPrincipal principal;
    private LocalDateTime originalDate;
    private Expense expense;

    @BeforeEach
    public void setup() {
        u1 = User.builder().id(1L).firebaseUid("uid1").name("User 1").email("u1@example.com").build();
        u2 = User.builder().id(2L).firebaseUid("uid2").name("User 2").email("u2@example.com").build();
        group = Group.builder().id(10L).name("Test Group").createdBy(u1).build();
        principal = new FirebaseUserPrincipal("uid1", "u1@example.com", "User 1", null);
        originalDate = LocalDateTime.of(2026, 8, 16, 20, 15, 0);

        ExpenseSplit s1 = ExpenseSplit.builder()
                .id(new ExpenseSplitId(100L, 1L))
                .user(u1)
                .amountOwed(BigDecimal.valueOf(15.00))
                .build();
        ExpenseSplit s2 = ExpenseSplit.builder()
                .id(new ExpenseSplitId(100L, 2L))
                .user(u2)
                .amountOwed(BigDecimal.valueOf(15.00))
                .build();

        List<ExpenseSplit> splits = new ArrayList<>(Arrays.asList(s1, s2));
        expense = Expense.builder()
                .id(100L)
                .group(group)
                .paidBy(u1)
                .amount(BigDecimal.valueOf(30.00))
                .description("Lunch")
                .date(originalDate)
                .splitType("EQUAL")
                .splits(splits)
                .build();
        s1.setExpense(expense);
        s2.setExpense(expense);

        org.mockito.Mockito.lenient().when(userService.convertToDto(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return UserDto.builder()
                    .id(user.getId())
                    .firebaseUid(user.getFirebaseUid())
                    .name(user.getName())
                    .email(user.getEmail())
                    .build();
        });
    }

    @Test
    public void getExpense_returnsDto_whenCallerIsGroupMember() {
        when(userService.getEntityByFirebaseUid("uid1")).thenReturn(u1);
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 1L)).thenReturn(true);

        ExpenseDto dto = expenseService.getExpense(100L, principal);

        assertEquals(100L, dto.getId());
        assertEquals(10L, dto.getGroupId());
        assertEquals("Lunch", dto.getDescription());
        assertEquals(0, dto.getAmount().compareTo(BigDecimal.valueOf(30.00)));
        assertEquals(originalDate, dto.getDate());
        assertEquals(2, dto.getSplits().size());
    }

    @Test
    public void getExpense_throwsNotFound_whenExpenseMissing() {
        when(userService.getEntityByFirebaseUid("uid1")).thenReturn(u1);
        when(expenseRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> expenseService.getExpense(999L, principal));
    }

    @Test
    public void getExpense_throwsNotFound_whenCallerNotInGroup() {
        when(userService.getEntityByFirebaseUid("uid1")).thenReturn(u1);
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 1L)).thenReturn(false);

        assertThrows(ResourceNotFoundException.class, () -> expenseService.getExpense(100L, principal));
    }

    @Test
    public void updateExpense_updatesFieldsRebuildsSplits_andPreservesDate() {
        when(userService.getEntityByFirebaseUid("uid1")).thenReturn(u1);
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 1L)).thenReturn(true);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 2L)).thenReturn(true);
        when(userService.getEntityById(2L)).thenReturn(u2);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setDescription("Dinner");
        request.setAmount(BigDecimal.valueOf(100.00));
        request.setPaidById(2L);
        request.setSplitWithUserIds(Arrays.asList(1L, 2L));

        when(userService.getEntityById(1L)).thenReturn(u1);

        ExpenseDto dto = expenseService.updateExpense(100L, request, principal);

        assertEquals("Dinner", dto.getDescription());
        assertEquals(0, dto.getAmount().compareTo(BigDecimal.valueOf(100.00)));
        assertEquals(2L, dto.getPaidBy().getId());
        assertEquals(originalDate, dto.getDate());
        assertEquals(2, dto.getSplits().size());
        assertEquals(0, dto.getSplits().get(0).getAmountOwed().compareTo(BigDecimal.valueOf(50.00)));
        assertEquals(0, dto.getSplits().get(1).getAmountOwed().compareTo(BigDecimal.valueOf(50.00)));
        verify(expenseRepository).save(expense);
    }

    @Test
    public void updateExpense_assignsRemainderToFirstSplit() {
        when(userService.getEntityByFirebaseUid("uid1")).thenReturn(u1);
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 1L)).thenReturn(true);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 2L)).thenReturn(true);
        when(userService.getEntityById(1L)).thenReturn(u1);
        when(userService.getEntityById(2L)).thenReturn(u2);
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setDescription("Coffee");
        request.setAmount(new BigDecimal("100.01"));
        request.setPaidById(1L);
        request.setSplitWithUserIds(Arrays.asList(1L, 2L));

        ExpenseDto dto = expenseService.updateExpense(100L, request, principal);

        assertEquals(0, dto.getSplits().get(0).getAmountOwed().compareTo(new BigDecimal("50.01")));
        assertEquals(0, dto.getSplits().get(1).getAmountOwed().compareTo(new BigDecimal("50.00")));
        assertEquals(originalDate, dto.getDate());
    }

    @Test
    public void updateExpense_throwsBadRequest_whenPayerNotInGroup() {
        when(userService.getEntityByFirebaseUid("uid1")).thenReturn(u1);
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 1L)).thenReturn(true);
        when(userService.getEntityById(2L)).thenReturn(u2);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 2L)).thenReturn(false);

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setDescription("Dinner");
        request.setAmount(BigDecimal.valueOf(50.00));
        request.setPaidById(2L);

        assertThrows(BadRequestException.class, () -> expenseService.updateExpense(100L, request, principal));
        verify(expenseRepository, never()).save(any());
    }

    @Test
    public void updateExpense_throwsNotFound_whenCallerNotInGroup() {
        when(userService.getEntityByFirebaseUid("uid1")).thenReturn(u1);
        when(expenseRepository.findById(100L)).thenReturn(Optional.of(expense));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 1L)).thenReturn(false);

        UpdateExpenseRequest request = new UpdateExpenseRequest();
        request.setDescription("Dinner");
        request.setAmount(BigDecimal.valueOf(50.00));

        assertThrows(ResourceNotFoundException.class, () -> expenseService.updateExpense(100L, request, principal));
        verify(expenseRepository, never()).save(any());
    }
}
