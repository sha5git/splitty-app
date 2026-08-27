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
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.Optional;

@Service
public class NotificationCopyService {

    private static final int MAX_DESCRIPTION_LENGTH = 40;

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;

    public NotificationCopyService(
            GroupRepository groupRepository,
            UserRepository userRepository,
            ExpenseRepository expenseRepository,
            SettlementRepository settlementRepository) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
    }

    public Optional<NotificationCopy> compose(GroupChangeEvent event, Long recipientUserId) {
        if (event == null || event.getType() == null || recipientUserId == null) {
            return Optional.empty();
        }

        String type = event.getType();
        if ("GROUP_CREATED".equals(type)
                && event.getActorUserId() != null
                && event.getActorUserId().equals(recipientUserId)) {
            return Optional.empty();
        }

        String groupName = event.getGroupId() == null
                ? "Splitty"
                : groupRepository.findById(event.getGroupId()).map(Group::getName).orElse("Splitty");
        String actor = displayName(event.getActorUserId(), recipientUserId, true);

        return switch (type) {
            case "EXPENSE_CREATED" -> expenseCopy(event, recipientUserId, groupName, actor, "added");
            case "EXPENSE_UPDATED" -> expenseCopy(event, recipientUserId, groupName, actor, "updated");
            case "EXPENSE_DELETED" -> Optional.of(new NotificationCopy(groupName, actor + " removed an expense"));
            case "SETTLEMENT_CREATED" -> settlementCreatedCopy(event, recipientUserId, groupName);
            case "SETTLEMENT_UPDATED" -> Optional.of(new NotificationCopy(groupName, actor + " updated a settlement"));
            case "GROUP_UPDATED" -> Optional.of(new NotificationCopy(
                    "Splitty", actor + " renamed the group to " + groupName));
            case "GROUP_CREATED" -> Optional.of(new NotificationCopy(
                    "Splitty", actor + " added you to " + groupName));
            case "MEMBER_ADDED" -> memberCopy(event, recipientUserId, groupName, actor, "added");
            case "MEMBER_REMOVED" -> memberCopy(event, recipientUserId, groupName, actor, "removed");
            default -> Optional.empty();
        };
    }

    private Optional<NotificationCopy> expenseCopy(
            GroupChangeEvent event, Long recipientUserId, String groupName, String actor, String verb) {
        if (event.getEntityId() == null) {
            return Optional.of(new NotificationCopy(groupName, actor + " " + verb + " an expense"));
        }
        Optional<Expense> expense = expenseRepository.findById(event.getEntityId());
        if (expense.isEmpty()) {
            return Optional.of(new NotificationCopy(groupName, actor + " " + verb + " an expense"));
        }
        Expense row = expense.get();
        String description = truncate(row.getDescription());
        String body = actor + " " + verb + " " + description + " · " + formatInr(row.getAmount());
        return Optional.of(new NotificationCopy(groupName, body));
    }

    private Optional<NotificationCopy> settlementCreatedCopy(
            GroupChangeEvent event, Long recipientUserId, String groupName) {
        if (event.getEntityId() == null) {
            return Optional.empty();
        }
        Optional<Settlement> settlement = settlementRepository.findById(event.getEntityId());
        if (settlement.isEmpty()) {
            String actor = displayName(event.getActorUserId(), recipientUserId, true);
            return Optional.of(new NotificationCopy(groupName, actor + " recorded a settlement"));
        }
        Settlement row = settlement.get();
        String from = displayName(row.getFromUser(), recipientUserId, true);
        String to = displayName(row.getToUser(), recipientUserId, false);
        String body = from + " paid " + to + " " + formatInr(row.getAmount());
        return Optional.of(new NotificationCopy(groupName, body));
    }

    private Optional<NotificationCopy> memberCopy(
            GroupChangeEvent event, Long recipientUserId, String groupName, String actor, String verb) {
        String target = displayName(event.getEntityId(), recipientUserId, false);
        String preposition = "removed".equals(verb) ? "from" : "to";
        return Optional.of(new NotificationCopy(
                groupName, actor + " " + verb + " " + target + " " + preposition + " the group"));
    }

    private String displayName(Long userId, Long recipientUserId, boolean capitalizeYou) {
        if (userId == null) {
            return "Someone";
        }
        return userRepository.findById(userId)
                .map(user -> displayName(user, recipientUserId, capitalizeYou))
                .orElse("Someone");
    }

    private String displayName(User user, Long recipientUserId, boolean capitalizeYou) {
        if (user.getId() != null && user.getId().equals(recipientUserId)) {
            return capitalizeYou ? "You" : "you";
        }
        if (user.getName() == null || user.getName().isBlank()) {
            return "Someone";
        }
        return user.getName().trim();
    }

    static String formatInr(BigDecimal amount) {
        if (amount == null) {
            return "₹0";
        }
        NumberFormat format = NumberFormat.getNumberInstance(Locale.forLanguageTag("en-IN"));
        format.setMaximumFractionDigits(2);
        format.setMinimumFractionDigits(0);
        return "₹" + format.format(amount);
    }

    static String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "an expense";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= MAX_DESCRIPTION_LENGTH) {
            return trimmed;
        }
        return trimmed.substring(0, MAX_DESCRIPTION_LENGTH - 1) + "…";
    }
}
