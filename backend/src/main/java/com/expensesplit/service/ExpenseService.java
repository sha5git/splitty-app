package com.expensesplit.service;

import com.expensesplit.dto.CreateExpenseRequest;
import com.expensesplit.dto.ExpenseDto;
import com.expensesplit.dto.ExpenseSplitDto;
import com.expensesplit.dto.UpdateExpenseRequest;
import com.expensesplit.entity.*;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.repository.ExpenseRepository;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.security.FirebaseUserPrincipal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupService groupService;
    private final UserService userService;

    public ExpenseService(ExpenseRepository expenseRepository,
                          GroupMemberRepository groupMemberRepository,
                          GroupService groupService,
                          UserService userService) {
        this.expenseRepository = expenseRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupService = groupService;
        this.userService = userService;
    }

    @Transactional
    public ExpenseDto createExpense(Long groupId, CreateExpenseRequest request, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());
        requireGroupMembership(groupId, currentUser.getId());

        Group group = groupService.getEntityById(groupId);
        User paidBy = resolvePayer(groupId, request.getPaidById(), currentUser);
        List<User> splitUsers = resolveSplitUsers(groupId, request.getSplitWithUserIds());

        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidBy)
                .amount(request.getAmount())
                .description(request.getDescription())
                .date(request.getDate() != null ? request.getDate() : LocalDateTime.now())
                .splitType("EQUAL")
                .build();

        expense.setSplits(buildEqualSplits(expense, splitUsers, request.getAmount()));
        expense = expenseRepository.save(expense);
        for (ExpenseSplit split : expense.getSplits()) {
            split.getId().setExpenseId(expense.getId());
        }
        expense = expenseRepository.save(expense);

        return convertToDto(expense);
    }

    public List<ExpenseDto> listExpenses(Long groupId, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());
        requireGroupMembership(groupId, currentUser.getId());

        List<Expense> expenses = expenseRepository.findByGroupIdOrderByDateDesc(groupId);
        return expenses.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public ExpenseDto getExpense(Long expenseId, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());
        Expense expense = findAccessibleExpense(expenseId, currentUser.getId());
        return convertToDto(expense);
    }

    @Transactional
    public ExpenseDto updateExpense(Long expenseId, UpdateExpenseRequest request, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());
        Expense expense = findAccessibleExpense(expenseId, currentUser.getId());
        Long groupId = expense.getGroup().getId();

        User paidBy = resolvePayer(groupId, request.getPaidById(), currentUser);
        List<User> splitUsers = resolveSplitUsers(groupId, request.getSplitWithUserIds());

        expense.setDescription(request.getDescription());
        expense.setAmount(request.getAmount());
        expense.setPaidBy(paidBy);
        expense.setSplitType("EQUAL");
        // Preserve original date

        expense.getSplits().clear();
        expense.getSplits().addAll(buildEqualSplits(expense, splitUsers, request.getAmount()));
        for (ExpenseSplit split : expense.getSplits()) {
            split.getId().setExpenseId(expense.getId());
        }

        return convertToDto(expenseRepository.save(expense));
    }

    @Transactional
    public void deleteExpense(Long expenseId, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());
        Expense expense = findAccessibleExpense(expenseId, currentUser.getId());
        expenseRepository.delete(expense);
    }

    public ExpenseDto convertToDto(Expense expense) {
        if (expense == null) return null;

        List<ExpenseSplitDto> splitDtos = expense.getSplits().stream()
                .map(split -> ExpenseSplitDto.builder()
                        .user(userService.convertToDto(split.getUser()))
                        .amountOwed(split.getAmountOwed())
                        .build())
                .collect(Collectors.toList());

        return ExpenseDto.builder()
                .id(expense.getId())
                .groupId(expense.getGroup().getId())
                .paidBy(userService.convertToDto(expense.getPaidBy()))
                .amount(expense.getAmount())
                .description(expense.getDescription())
                .date(expense.getDate())
                .splitType(expense.getSplitType())
                .splits(splitDtos)
                .build();
    }

    private Expense findAccessibleExpense(Long expenseId, Long userId) {
        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + expenseId));

        requireGroupMembership(expense.getGroup().getId(), userId);
        return expense;
    }

    private void requireGroupMembership(Long groupId, Long userId) {
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, userId)) {
            throw new ResourceNotFoundException("Group not found or access denied");
        }
    }

    private User resolvePayer(Long groupId, Long paidById, User currentUser) {
        if (paidById == null) {
            return currentUser;
        }
        User paidBy = userService.getEntityById(paidById);
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, paidBy.getId())) {
            throw new BadRequestException("Payer is not a member of the group");
        }
        return paidBy;
    }

    private List<User> resolveSplitUsers(Long groupId, List<Long> splitWithUserIds) {
        List<User> splitUsers = new ArrayList<>();
        if (splitWithUserIds != null && !splitWithUserIds.isEmpty()) {
            for (Long userId : splitWithUserIds) {
                User user = userService.getEntityById(userId);
                if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, user.getId())) {
                    throw new BadRequestException("User to split with is not a member of the group: " + user.getName());
                }
                splitUsers.add(user);
            }
        } else {
            splitUsers = groupMemberRepository.findByGroupId(groupId)
                    .stream()
                    .map(GroupMember::getUser)
                    .collect(Collectors.toList());
        }

        if (splitUsers.isEmpty()) {
            throw new BadRequestException("No members to split the expense with");
        }
        return splitUsers;
    }

    private List<ExpenseSplit> buildEqualSplits(Expense expense, List<User> splitUsers, BigDecimal amount) {
        int N = splitUsers.size();
        BigDecimal baseShare = amount.divide(BigDecimal.valueOf(N), 2, RoundingMode.DOWN);
        BigDecimal remainder = amount.subtract(baseShare.multiply(BigDecimal.valueOf(N)));

        List<ExpenseSplit> splits = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            User user = splitUsers.get(i);
            BigDecimal share = i == 0 ? baseShare.add(remainder) : baseShare;

            ExpenseSplitId splitId = new ExpenseSplitId(expense.getId(), user.getId());
            ExpenseSplit split = ExpenseSplit.builder()
                    .id(splitId)
                    .expense(expense)
                    .user(user)
                    .amountOwed(share)
                    .build();
            splits.add(split);
        }
        return splits;
    }
}
