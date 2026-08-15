package com.expensesplit.service;

import com.expensesplit.dto.CreateExpenseRequest;
import com.expensesplit.dto.ExpenseDto;
import com.expensesplit.dto.ExpenseSplitDto;
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

        // Security check: current user must be in the group
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, currentUser.getId())) {
            throw new ResourceNotFoundException("Group not found or access denied");
        }

        Group group = groupService.getEntityById(groupId);

        // Determine who paid
        User paidBy;
        if (request.getPaidById() != null) {
            paidBy = userService.getEntityById(request.getPaidById());
            if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, paidBy.getId())) {
                throw new BadRequestException("Payer is not a member of the group");
            }
        } else {
            paidBy = currentUser;
        }

        // Determine split members
        List<User> splitUsers = new ArrayList<>();
        if (request.getSplitWithUserIds() != null && !request.getSplitWithUserIds().isEmpty()) {
            for (Long userId : request.getSplitWithUserIds()) {
                User user = userService.getEntityById(userId);
                if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, user.getId())) {
                    throw new BadRequestException("User to split with is not a member of the group: " + user.getName());
                }
                splitUsers.add(user);
            }
        } else {
            // Default to all group members
            splitUsers = groupMemberRepository.findByGroupId(groupId)
                    .stream()
                    .map(GroupMember::getUser)
                    .collect(Collectors.toList());
        }

        if (splitUsers.isEmpty()) {
            throw new BadRequestException("No members to split the expense with");
        }

        // Create the Expense
        Expense expense = Expense.builder()
                .group(group)
                .paidBy(paidBy)
                .amount(request.getAmount())
                .description(request.getDescription())
                .date(request.getDate() != null ? request.getDate() : LocalDateTime.now())
                .splitType("EQUAL")
                .build();

        // Calculate Splits (Equal split with remainder adjustment)
        int N = splitUsers.size();
        BigDecimal amount = request.getAmount();
        BigDecimal baseShare = amount.divide(BigDecimal.valueOf(N), 2, RoundingMode.DOWN);
        BigDecimal remainder = amount.subtract(baseShare.multiply(BigDecimal.valueOf(N)));

        List<ExpenseSplit> splits = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            User user = splitUsers.get(i);
            BigDecimal share = baseShare;
            if (i == 0) {
                share = baseShare.add(remainder);
            }

            ExpenseSplitId splitId = new ExpenseSplitId(null, user.getId());
            ExpenseSplit split = ExpenseSplit.builder()
                    .id(splitId)
                    .expense(expense)
                    .user(user)
                    .amountOwed(share)
                    .build();
            splits.add(split);
        }
        expense.setSplits(splits);

        // Save expense first, then set the correct composite key expense ID, then save again
        expense = expenseRepository.save(expense);
        for (ExpenseSplit split : splits) {
            split.getId().setExpenseId(expense.getId());
        }
        expense = expenseRepository.save(expense);

        return convertToDto(expense);
    }

    public List<ExpenseDto> listExpenses(Long groupId, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());

        // Security check
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, currentUser.getId())) {
            throw new ResourceNotFoundException("Group not found or access denied");
        }

        List<Expense> expenses = expenseRepository.findByGroupIdOrderByDateDesc(groupId);
        return expenses.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Transactional
    public void deleteExpense(Long expenseId, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());

        Expense expense = expenseRepository.findById(expenseId)
                .orElseThrow(() -> new ResourceNotFoundException("Expense not found with id: " + expenseId));

        // Security check: must be in the group of the expense
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(expense.getGroup().getId(), currentUser.getId())) {
            throw new ResourceNotFoundException("Group not found or access denied");
        }

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
}
