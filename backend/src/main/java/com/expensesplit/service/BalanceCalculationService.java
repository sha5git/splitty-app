package com.expensesplit.service;

import com.expensesplit.dto.BalanceDto;
import com.expensesplit.entity.*;
import com.expensesplit.repository.*;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
public class BalanceCalculationService {

    private final GroupMemberRepository groupMemberRepository;
    private final ExpenseRepository expenseRepository;
    private final SettlementRepository settlementRepository;
    private final UserService userService;

    public BalanceCalculationService(GroupMemberRepository groupMemberRepository,
                                     ExpenseRepository expenseRepository,
                                     SettlementRepository settlementRepository,
                                     UserService userService) {
        this.groupMemberRepository = groupMemberRepository;
        this.expenseRepository = expenseRepository;
        this.settlementRepository = settlementRepository;
        this.userService = userService;
    }

    public List<BalanceDto> calculateBalances(Long groupId) {
        NetPositionSnapshot snapshot = computeNetPositions(groupId);
        if (snapshot.netPositions().isEmpty()) {
            return Collections.emptyList();
        }

        return resolveDebts(snapshot.netPositions(), snapshot.userMap());
    }

    /**
     * Net position for a user in a group before debt simplification.
     * Positive means the user is owed money; negative means they owe money.
     */
    public BigDecimal calculateUserNetBalance(Long groupId, Long userId) {
        Map<Long, BigDecimal> netPositions = computeNetPositions(groupId).netPositions();
        return netPositions.getOrDefault(userId, BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private NetPositionSnapshot computeNetPositions(Long groupId) {
        List<GroupMember> members = groupMemberRepository.findByGroupId(groupId);
        if (members.isEmpty()) {
            return new NetPositionSnapshot(Collections.emptyMap(), Collections.emptyMap());
        }

        Map<Long, User> userMap = new HashMap<>();
        Map<Long, BigDecimal> netPositions = new HashMap<>();

        for (GroupMember member : members) {
            User user = member.getUser();
            userMap.put(user.getId(), user);
            netPositions.put(user.getId(), BigDecimal.ZERO);
        }

        List<Expense> expenses = expenseRepository.findByGroupIdOrderByDateDesc(groupId);
        for (Expense expense : expenses) {
            Long payerId = expense.getPaidBy().getId();

            if (netPositions.containsKey(payerId)) {
                netPositions.put(payerId, netPositions.get(payerId).add(expense.getAmount()));
            }

            for (ExpenseSplit split : expense.getSplits()) {
                Long owerId = split.getUser().getId();
                if (netPositions.containsKey(owerId)) {
                    netPositions.put(owerId, netPositions.get(owerId).subtract(split.getAmountOwed()));
                }
            }
        }

        List<Settlement> settlements = settlementRepository.findByGroupIdOrderByDateDesc(groupId);
        for (Settlement settlement : settlements) {
            Long fromId = settlement.getFromUser().getId();
            Long toId = settlement.getToUser().getId();

            if (netPositions.containsKey(fromId)) {
                netPositions.put(fromId, netPositions.get(fromId).add(settlement.getAmount()));
            }
            if (netPositions.containsKey(toId)) {
                netPositions.put(toId, netPositions.get(toId).subtract(settlement.getAmount()));
            }
        }

        return new NetPositionSnapshot(netPositions, userMap);
    }

    private List<BalanceDto> resolveDebts(Map<Long, BigDecimal> netPositions, Map<Long, User> userMap) {
        // Represent balances as lists of Debtors and Creditors
        List<UserBalance> debtors = new ArrayList<>();
        List<UserBalance> creditors = new ArrayList<>();

        for (Map.Entry<Long, BigDecimal> entry : netPositions.entrySet()) {
            BigDecimal balance = entry.getValue().setScale(2, RoundingMode.HALF_UP);
            User user = userMap.get(entry.getKey());

            if (balance.compareTo(BigDecimal.ZERO) < 0) {
                debtors.add(new UserBalance(user, balance));
            } else if (balance.compareTo(BigDecimal.ZERO) > 0) {
                creditors.add(new UserBalance(user, balance));
            }
        }

        List<BalanceDto> balances = new ArrayList<>();

        // Greedy matching
        while (!debtors.isEmpty() && !creditors.isEmpty()) {
            // Sort to match largest debtors with largest creditors
            debtors.sort(Comparator.comparing(UserBalance::getBalance)); // Ascending (most negative first)
            creditors.sort((c1, c2) -> c2.getBalance().compareTo(c1.getBalance())); // Descending (most positive first)

            UserBalance debtor = debtors.get(0);
            UserBalance creditor = creditors.get(0);

            BigDecimal oweAmount = debtor.getBalance().negate();
            BigDecimal receiveAmount = creditor.getBalance();

            BigDecimal transferAmount = oweAmount.min(receiveAmount).setScale(2, RoundingMode.HALF_UP);

            if (transferAmount.compareTo(BigDecimal.valueOf(0.01)) < 0) {
                break; // Ignore sub-cent balances
            }

            balances.add(BalanceDto.builder()
                    .fromUser(userService.convertToDto(debtor.getUser()))
                    .toUser(userService.convertToDto(creditor.getUser()))
                    .amount(transferAmount)
                    .build());

            // Update balances
            debtor.setBalance(debtor.getBalance().add(transferAmount));
            creditor.setBalance(creditor.getBalance().subtract(transferAmount));

            // Remove settled users
            if (debtor.getBalance().abs().compareTo(BigDecimal.valueOf(0.005)) < 0) {
                debtors.remove(0);
            }
            if (creditor.getBalance().abs().compareTo(BigDecimal.valueOf(0.005)) < 0) {
                creditors.remove(0);
            }
        }

        return balances;
    }

    private record NetPositionSnapshot(Map<Long, BigDecimal> netPositions, Map<Long, User> userMap) {}

    private static class UserBalance {
        private final User user;
        private BigDecimal balance;

        public UserBalance(User user, BigDecimal balance) {
            this.user = user;
            this.balance = balance;
        }

        public User getUser() { return user; }
        public BigDecimal getBalance() { return balance; }
        public void setBalance(BigDecimal balance) { this.balance = balance; }
    }
}
