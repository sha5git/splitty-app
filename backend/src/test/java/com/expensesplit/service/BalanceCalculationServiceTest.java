package com.expensesplit.service;

import com.expensesplit.dto.BalanceDto;
import com.expensesplit.dto.UserDto;
import com.expensesplit.entity.*;
import com.expensesplit.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BalanceCalculationServiceTest {

    @InjectMocks
    private BalanceCalculationService balanceCalculationService;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private ExpenseRepository expenseRepository;

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private UserService userService;

    private User u1;
    private User u2;
    private User u3;
    private Group group;
    private List<GroupMember> members;

    @BeforeEach
    public void setup() {
        u1 = User.builder().id(1L).firebaseUid("uid1").name("User 1").email("u1@example.com").build();
        u2 = User.builder().id(2L).firebaseUid("uid2").name("User 2").email("u2@example.com").build();
        u3 = User.builder().id(3L).firebaseUid("uid3").name("User 3").email("u3@example.com").build();

        group = Group.builder().id(10L).name("Test Group").createdBy(u1).build();

        GroupMember gm1 = GroupMember.builder().id(new GroupMemberId(10L, 1L)).group(group).user(u1).build();
        GroupMember gm2 = GroupMember.builder().id(new GroupMemberId(10L, 2L)).group(group).user(u2).build();
        GroupMember gm3 = GroupMember.builder().id(new GroupMemberId(10L, 3L)).group(group).user(u3).build();

        members = Arrays.asList(gm1, gm2, gm3);

        // Mock conversion behavior in UserService
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
    public void testUserNetBalanceEvenSplit() {
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(members);

        Expense expense = Expense.builder()
                .id(100L)
                .group(group)
                .paidBy(u1)
                .amount(BigDecimal.valueOf(30.00))
                .description("Lunch")
                .date(LocalDateTime.now())
                .splitType("EQUAL")
                .build();

        ExpenseSplit s1 = ExpenseSplit.builder().id(new ExpenseSplitId(100L, 1L)).expense(expense).user(u1).amountOwed(BigDecimal.valueOf(10.00)).build();
        ExpenseSplit s2 = ExpenseSplit.builder().id(new ExpenseSplitId(100L, 2L)).expense(expense).user(u2).amountOwed(BigDecimal.valueOf(10.00)).build();
        ExpenseSplit s3 = ExpenseSplit.builder().id(new ExpenseSplitId(100L, 3L)).expense(expense).user(u3).amountOwed(BigDecimal.valueOf(10.00)).build();
        expense.setSplits(Arrays.asList(s1, s2, s3));

        when(expenseRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Collections.singletonList(expense));
        when(settlementRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Collections.emptyList());

        assertEquals(0, balanceCalculationService.calculateUserNetBalance(10L, 1L).compareTo(BigDecimal.valueOf(20.00)));
        assertEquals(0, balanceCalculationService.calculateUserNetBalance(10L, 2L).compareTo(BigDecimal.valueOf(-10.00)));
        assertEquals(0, balanceCalculationService.calculateUserNetBalance(10L, 3L).compareTo(BigDecimal.valueOf(-10.00)));
    }

    @Test
    public void testEvenSplit() {
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(members);

        // Expense: U1 pays ₹30.00, split equally with U1, U2, U3 (each owes ₹10.00)
        Expense expense = Expense.builder()
                .id(100L)
                .group(group)
                .paidBy(u1)
                .amount(BigDecimal.valueOf(30.00))
                .description("Lunch")
                .date(LocalDateTime.now())
                .splitType("EQUAL")
                .build();

        ExpenseSplit s1 = ExpenseSplit.builder().id(new ExpenseSplitId(100L, 1L)).expense(expense).user(u1).amountOwed(BigDecimal.valueOf(10.00)).build();
        ExpenseSplit s2 = ExpenseSplit.builder().id(new ExpenseSplitId(100L, 2L)).expense(expense).user(u2).amountOwed(BigDecimal.valueOf(10.00)).build();
        ExpenseSplit s3 = ExpenseSplit.builder().id(new ExpenseSplitId(100L, 3L)).expense(expense).user(u3).amountOwed(BigDecimal.valueOf(10.00)).build();
        expense.setSplits(Arrays.asList(s1, s2, s3));

        when(expenseRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Collections.singletonList(expense));
        when(settlementRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Collections.emptyList());

        List<BalanceDto> balances = balanceCalculationService.calculateBalances(10L);

        // Expect two balances:
        // U2 owes U1 ₹10
        // U3 owes U1 ₹10
        assertEquals(2, balances.size());

        BalanceDto b1 = findBalance(balances, 2L, 1L);
        assertNotNull(b1);
        assertEquals(0, b1.getAmount().compareTo(BigDecimal.valueOf(10.00)));

        BalanceDto b2 = findBalance(balances, 3L, 1L);
        assertNotNull(b2);
        assertEquals(0, b2.getAmount().compareTo(BigDecimal.valueOf(10.00)));
    }

    @Test
    public void testUnevenPaidAmounts() {
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(members);

        // Expense 1: U1 pays ₹20.00, splits: U1: 6.68, U2: 6.66, U3: 6.66
        Expense expense1 = Expense.builder()
                .id(101L)
                .group(group)
                .paidBy(u1)
                .amount(BigDecimal.valueOf(20.00))
                .description("Drinks")
                .date(LocalDateTime.now())
                .splitType("EQUAL")
                .build();
        ExpenseSplit s1_1 = ExpenseSplit.builder().id(new ExpenseSplitId(101L, 1L)).expense(expense1).user(u1).amountOwed(BigDecimal.valueOf(6.68)).build();
        ExpenseSplit s1_2 = ExpenseSplit.builder().id(new ExpenseSplitId(101L, 2L)).expense(expense1).user(u2).amountOwed(BigDecimal.valueOf(6.66)).build();
        ExpenseSplit s1_3 = ExpenseSplit.builder().id(new ExpenseSplitId(101L, 3L)).expense(expense1).user(u3).amountOwed(BigDecimal.valueOf(6.66)).build();
        expense1.setSplits(Arrays.asList(s1_1, s1_2, s1_3));

        // Expense 2: U2 pays ₹10.00, splits: U1: 3.34, U2: 3.33, U3: 3.33
        Expense expense2 = Expense.builder()
                .id(102L)
                .group(group)
                .paidBy(u2)
                .amount(BigDecimal.valueOf(10.00))
                .description("Snacks")
                .date(LocalDateTime.now())
                .splitType("EQUAL")
                .build();
        ExpenseSplit s2_1 = ExpenseSplit.builder().id(new ExpenseSplitId(102L, 1L)).expense(expense2).user(u1).amountOwed(BigDecimal.valueOf(3.34)).build();
        ExpenseSplit s2_2 = ExpenseSplit.builder().id(new ExpenseSplitId(102L, 2L)).expense(expense2).user(u2).amountOwed(BigDecimal.valueOf(3.33)).build();
        ExpenseSplit s2_3 = ExpenseSplit.builder().id(new ExpenseSplitId(102L, 3L)).expense(expense2).user(u3).amountOwed(BigDecimal.valueOf(3.33)).build();
        expense2.setSplits(Arrays.asList(s2_1, s2_2, s2_3));

        when(expenseRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Arrays.asList(expense1, expense2));
        when(settlementRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Collections.emptyList());

        List<BalanceDto> balances = balanceCalculationService.calculateBalances(10L);

        // Owed: U1: 10.02, U2: 9.99, U3: 9.99
        // Paid: U1: 20.00, U2: 10.00, U3: 0.00
        // Net: U1: +9.98, U2: +0.01, U3: -9.99
        // Expected simplified balances:
        // U3 owes U1 ₹9.98
        // U3 owes U2 ₹0.01
        assertEquals(2, balances.size());

        BalanceDto b1 = findBalance(balances, 3L, 1L);
        assertNotNull(b1);
        assertEquals(0, b1.getAmount().compareTo(BigDecimal.valueOf(9.98)));

        BalanceDto b2 = findBalance(balances, 3L, 2L);
        assertNotNull(b2);
        assertEquals(0, b2.getAmount().compareTo(BigDecimal.valueOf(0.01)));
    }

    @Test
    public void testPartialSettlement() {
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(members);

        // Expense: U1 pays ₹30.00, splits: U1: 10, U2: 10, U3: 10
        Expense expense = Expense.builder()
                .id(100L)
                .group(group)
                .paidBy(u1)
                .amount(BigDecimal.valueOf(30.00))
                .description("Lunch")
                .date(LocalDateTime.now())
                .splitType("EQUAL")
                .build();
        ExpenseSplit s1 = ExpenseSplit.builder().id(new ExpenseSplitId(100L, 1L)).expense(expense).user(u1).amountOwed(BigDecimal.valueOf(10.00)).build();
        ExpenseSplit s2 = ExpenseSplit.builder().id(new ExpenseSplitId(100L, 2L)).expense(expense).user(u2).amountOwed(BigDecimal.valueOf(10.00)).build();
        ExpenseSplit s3 = ExpenseSplit.builder().id(new ExpenseSplitId(100L, 3L)).expense(expense).user(u3).amountOwed(BigDecimal.valueOf(10.00)).build();
        expense.setSplits(Arrays.asList(s1, s2, s3));

        // Settlement: U2 pays U1 ₹5.00 (partial settlement)
        Settlement settlement = Settlement.builder()
                .id(200L)
                .group(group)
                .fromUser(u2)
                .toUser(u1)
                .amount(BigDecimal.valueOf(5.00))
                .date(LocalDateTime.now())
                .build();

        when(expenseRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Collections.singletonList(expense));
        when(settlementRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Collections.singletonList(settlement));

        List<BalanceDto> balances = balanceCalculationService.calculateBalances(10L);

        // Expected:
        // U2 owes U1 ₹5.00 (down from 10)
        // U3 owes U1 ₹10.00
        assertEquals(2, balances.size());

        BalanceDto b1 = findBalance(balances, 2L, 1L);
        assertNotNull(b1);
        assertEquals(0, b1.getAmount().compareTo(BigDecimal.valueOf(5.00)));

        BalanceDto b2 = findBalance(balances, 3L, 1L);
        assertNotNull(b2);
        assertEquals(0, b2.getAmount().compareTo(BigDecimal.valueOf(10.00)));
    }

    @Test
    public void testCompleteSettlement() {
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(members);

        // Expense: U1 pays ₹30.00, splits: U1: 10, U2: 10, U3: 10
        Expense expense = Expense.builder()
                .id(100L)
                .group(group)
                .paidBy(u1)
                .amount(BigDecimal.valueOf(30.00))
                .description("Lunch")
                .date(LocalDateTime.now())
                .splitType("EQUAL")
                .build();
        ExpenseSplit s1 = ExpenseSplit.builder().id(new ExpenseSplitId(100L, 1L)).expense(expense).user(u1).amountOwed(BigDecimal.valueOf(10.00)).build();
        ExpenseSplit s2 = ExpenseSplit.builder().id(new ExpenseSplitId(100L, 2L)).expense(expense).user(u2).amountOwed(BigDecimal.valueOf(10.00)).build();
        ExpenseSplit s3 = ExpenseSplit.builder().id(new ExpenseSplitId(100L, 3L)).expense(expense).user(u3).amountOwed(BigDecimal.valueOf(10.00)).build();
        expense.setSplits(Arrays.asList(s1, s2, s3));

        // Settlement: U2 pays U1 ₹10.00, U3 pays U1 ₹10.00
        Settlement settlement1 = Settlement.builder().id(201L).group(group).fromUser(u2).toUser(u1).amount(BigDecimal.valueOf(10.00)).date(LocalDateTime.now()).build();
        Settlement settlement2 = Settlement.builder().id(202L).group(group).fromUser(u3).toUser(u1).amount(BigDecimal.valueOf(10.00)).date(LocalDateTime.now()).build();

        when(expenseRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Collections.singletonList(expense));
        when(settlementRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Arrays.asList(settlement1, settlement2));

        List<BalanceDto> balances = balanceCalculationService.calculateBalances(10L);

        // Expected:
        // All debts resolved (balances should be empty)
        assertTrue(balances.isEmpty());
    }

    @Test
    public void testTwoCreditorsTwoDebtors() {
        // This scenario needs a 4th member (Chris) beyond the 3 set up in setup(),
        // so build a local members list for this test only.
        User u4 = User.builder().id(4L).firebaseUid("uid4").name("User 4").email("u4@example.com").build();
        GroupMember gm4 = GroupMember.builder().id(new GroupMemberId(10L, 4L)).group(group).user(u4).build();

        List<GroupMember> fourMembers = Arrays.asList(members.get(0), members.get(1), members.get(2), gm4);
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(fourMembers);

        // u1 = Alice, u2 = Bob, u3 = Trent, u4 = Chris

        // Expense 1: Trent (u3) pays ₹6340.00, split 4 ways -> 1585.00 each
        Expense trainRide = Expense.builder()
                .id(103L)
                .group(group)
                .paidBy(u3)
                .amount(BigDecimal.valueOf(6340.00))
                .description("Train Ride")
                .date(LocalDateTime.now())
                .splitType("EQUAL")
                .build();
        ExpenseSplit t1 = ExpenseSplit.builder().id(new ExpenseSplitId(103L, 1L)).expense(trainRide).user(u1).amountOwed(BigDecimal.valueOf(1585.00)).build();
        ExpenseSplit t2 = ExpenseSplit.builder().id(new ExpenseSplitId(103L, 2L)).expense(trainRide).user(u2).amountOwed(BigDecimal.valueOf(1585.00)).build();
        ExpenseSplit t3 = ExpenseSplit.builder().id(new ExpenseSplitId(103L, 3L)).expense(trainRide).user(u3).amountOwed(BigDecimal.valueOf(1585.00)).build();
        ExpenseSplit t4 = ExpenseSplit.builder().id(new ExpenseSplitId(103L, 4L)).expense(trainRide).user(u4).amountOwed(BigDecimal.valueOf(1585.00)).build();
        trainRide.setSplits(Arrays.asList(t1, t2, t3, t4));

        // Expense 2: Chris (u4) pays ₹2853.32, split 4 ways -> 713.33 each
        Expense cabRide = Expense.builder()
                .id(104L)
                .group(group)
                .paidBy(u4)
                .amount(BigDecimal.valueOf(2853.32))
                .description("Cab Ride")
                .date(LocalDateTime.now())
                .splitType("EQUAL")
                .build();
        ExpenseSplit c1 = ExpenseSplit.builder().id(new ExpenseSplitId(104L, 1L)).expense(cabRide).user(u1).amountOwed(BigDecimal.valueOf(713.33)).build();
        ExpenseSplit c2 = ExpenseSplit.builder().id(new ExpenseSplitId(104L, 2L)).expense(cabRide).user(u2).amountOwed(BigDecimal.valueOf(713.33)).build();
        ExpenseSplit c3 = ExpenseSplit.builder().id(new ExpenseSplitId(104L, 3L)).expense(cabRide).user(u3).amountOwed(BigDecimal.valueOf(713.33)).build();
        ExpenseSplit c4 = ExpenseSplit.builder().id(new ExpenseSplitId(104L, 4L)).expense(cabRide).user(u4).amountOwed(BigDecimal.valueOf(713.33)).build();
        cabRide.setSplits(Arrays.asList(c1, c2, c3, c4));

        when(expenseRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Arrays.asList(trainRide, cabRide));
        when(settlementRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Collections.emptyList());

        List<BalanceDto> balances = balanceCalculationService.calculateBalances(10L);

        // Net: Trent +4041.67, Chris +554.99, Alice -2298.33, Bob -2298.33
        // Expected simplified balances (3 entries, not 4 raw pairwise debts):
        // Alice owes Trent 2298.33
        // Bob owes Trent 1743.34
        // Bob owes Chris 554.99
        assertEquals(3, balances.size());

        BalanceDto aliceToTrent = findBalance(balances, 1L, 3L);
        assertNotNull(aliceToTrent);
        assertEquals(0, aliceToTrent.getAmount().compareTo(BigDecimal.valueOf(2298.33)));

        BalanceDto bobToTrent = findBalance(balances, 2L, 3L);
        assertNotNull(bobToTrent);
        assertEquals(0, bobToTrent.getAmount().compareTo(BigDecimal.valueOf(1743.34)));

        BalanceDto bobToChris = findBalance(balances, 2L, 4L);
        assertNotNull(bobToChris);
        assertEquals(0, bobToChris.getAmount().compareTo(BigDecimal.valueOf(554.99)));

        // Conservation check: total flowing out of debtors == total flowing into creditors
        BigDecimal totalFromDebtors = balances.stream()
                .map(BalanceDto::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(0, totalFromDebtors.compareTo(BigDecimal.valueOf(4596.66)));
    }

    @Test
    public void testPartialSettlementWithMultipleCreditors() {
        // Same 4-person setup as testTwoCreditorsTwoDebtors
        User u4 = User.builder().id(4L).firebaseUid("uid4").name("User 4").email("u4@example.com").build();
        GroupMember gm4 = GroupMember.builder().id(new GroupMemberId(10L, 4L)).group(group).user(u4).build();

        List<GroupMember> fourMembers = Arrays.asList(members.get(0), members.get(1), members.get(2), gm4);
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(fourMembers);

        // u1 = Alice, u2 = Bob, u3 = Trent, u4 = Chris

        // Expense 1: Trent (u3) pays ₹6340.00, split 4 ways -> 1585.00 each
        Expense trainRide = Expense.builder()
                .id(103L)
                .group(group)
                .paidBy(u3)
                .amount(BigDecimal.valueOf(6340.00))
                .description("Train Ride")
                .date(LocalDateTime.now())
                .splitType("EQUAL")
                .build();
        ExpenseSplit t1 = ExpenseSplit.builder().id(new ExpenseSplitId(103L, 1L)).expense(trainRide).user(u1).amountOwed(BigDecimal.valueOf(1585.00)).build();
        ExpenseSplit t2 = ExpenseSplit.builder().id(new ExpenseSplitId(103L, 2L)).expense(trainRide).user(u2).amountOwed(BigDecimal.valueOf(1585.00)).build();
        ExpenseSplit t3 = ExpenseSplit.builder().id(new ExpenseSplitId(103L, 3L)).expense(trainRide).user(u3).amountOwed(BigDecimal.valueOf(1585.00)).build();
        ExpenseSplit t4 = ExpenseSplit.builder().id(new ExpenseSplitId(103L, 4L)).expense(trainRide).user(u4).amountOwed(BigDecimal.valueOf(1585.00)).build();
        trainRide.setSplits(Arrays.asList(t1, t2, t3, t4));

        // Expense 2: Chris (u4) pays ₹2853.32, split 4 ways -> 713.33 each
        Expense cabRide = Expense.builder()
                .id(104L)
                .group(group)
                .paidBy(u4)
                .amount(BigDecimal.valueOf(2853.32))
                .description("Cab Ride")
                .date(LocalDateTime.now())
                .splitType("EQUAL")
                .build();
        ExpenseSplit c1 = ExpenseSplit.builder().id(new ExpenseSplitId(104L, 1L)).expense(cabRide).user(u1).amountOwed(BigDecimal.valueOf(713.33)).build();
        ExpenseSplit c2 = ExpenseSplit.builder().id(new ExpenseSplitId(104L, 2L)).expense(cabRide).user(u2).amountOwed(BigDecimal.valueOf(713.33)).build();
        ExpenseSplit c3 = ExpenseSplit.builder().id(new ExpenseSplitId(104L, 3L)).expense(cabRide).user(u3).amountOwed(BigDecimal.valueOf(713.33)).build();
        ExpenseSplit c4 = ExpenseSplit.builder().id(new ExpenseSplitId(104L, 4L)).expense(cabRide).user(u4).amountOwed(BigDecimal.valueOf(713.33)).build();
        cabRide.setSplits(Arrays.asList(c1, c2, c3, c4));

        // Partial settlement: Bob (u2) pays Trent (u3) ₹800.00 out of the ₹1743.34 he owes Trent.
        // Deliberately less than the full amount, and deliberately NOT touching
        // Bob's separate ₹554.99 debt to Chris — that debt should be untouched.
        Settlement partialPayment = Settlement.builder()
                .id(203L)
                .group(group)
                .fromUser(u2)
                .toUser(u3)
                .amount(BigDecimal.valueOf(800.00))
                .date(LocalDateTime.now())
                .build();

        when(expenseRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Arrays.asList(trainRide, cabRide));
        when(settlementRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Collections.singletonList(partialPayment));

        List<BalanceDto> balances = balanceCalculationService.calculateBalances(10L);

        // Expected (down from the 3 entries in testTwoCreditorsTwoDebtors):
        // Alice owes Trent 2298.33            (unaffected by Bob's payment)
        // Bob owes Trent 1743.34 - 800.00 = 943.34   (reduced, not cleared)
        // Bob owes Chris 554.99               (unaffected — separate creditor)
        assertEquals(3, balances.size());

        BalanceDto aliceToTrent = findBalance(balances, 1L, 3L);
        assertNotNull(aliceToTrent);
        assertEquals(0, aliceToTrent.getAmount().compareTo(BigDecimal.valueOf(2298.33)));

        BalanceDto bobToTrent = findBalance(balances, 2L, 3L);
        assertNotNull(bobToTrent);
        assertEquals(0, bobToTrent.getAmount().compareTo(BigDecimal.valueOf(943.34)));

        BalanceDto bobToChris = findBalance(balances, 2L, 4L);
        assertNotNull(bobToChris);
        assertEquals(0, bobToChris.getAmount().compareTo(BigDecimal.valueOf(554.99)));
    }

    @Test
    public void testOverpaymentShiftsExcessToRemainingCreditor() {
        User u4 = User.builder().id(4L).firebaseUid("uid4").name("User 4").email("u4@example.com").build();
        GroupMember gm4 = GroupMember.builder().id(new GroupMemberId(10L, 4L)).group(group).user(u4).build();

        List<GroupMember> fourMembers = Arrays.asList(members.get(0), members.get(1), members.get(2), gm4);
        when(groupMemberRepository.findByGroupId(10L)).thenReturn(fourMembers);

        // u1 = Alice, u2 = Bob, u3 = Trent, u4 = Chris
        Expense trainRide = Expense.builder()
                .id(103L).group(group).paidBy(u3).amount(BigDecimal.valueOf(6340.00))
                .description("Train Ride").date(LocalDateTime.now()).splitType("EQUAL").build();
        ExpenseSplit t1 = ExpenseSplit.builder().id(new ExpenseSplitId(103L, 1L)).expense(trainRide).user(u1).amountOwed(BigDecimal.valueOf(1585.00)).build();
        ExpenseSplit t2 = ExpenseSplit.builder().id(new ExpenseSplitId(103L, 2L)).expense(trainRide).user(u2).amountOwed(BigDecimal.valueOf(1585.00)).build();
        ExpenseSplit t3 = ExpenseSplit.builder().id(new ExpenseSplitId(103L, 3L)).expense(trainRide).user(u3).amountOwed(BigDecimal.valueOf(1585.00)).build();
        ExpenseSplit t4 = ExpenseSplit.builder().id(new ExpenseSplitId(103L, 4L)).expense(trainRide).user(u4).amountOwed(BigDecimal.valueOf(1585.00)).build();
        trainRide.setSplits(Arrays.asList(t1, t2, t3, t4));

        Expense cabRide = Expense.builder()
                .id(104L).group(group).paidBy(u4).amount(BigDecimal.valueOf(2853.32))
                .description("Cab Ride").date(LocalDateTime.now()).splitType("EQUAL").build();
        ExpenseSplit c1 = ExpenseSplit.builder().id(new ExpenseSplitId(104L, 1L)).expense(cabRide).user(u1).amountOwed(BigDecimal.valueOf(713.33)).build();
        ExpenseSplit c2 = ExpenseSplit.builder().id(new ExpenseSplitId(104L, 2L)).expense(cabRide).user(u2).amountOwed(BigDecimal.valueOf(713.33)).build();
        ExpenseSplit c3 = ExpenseSplit.builder().id(new ExpenseSplitId(104L, 3L)).expense(cabRide).user(u3).amountOwed(BigDecimal.valueOf(713.33)).build();
        ExpenseSplit c4 = ExpenseSplit.builder().id(new ExpenseSplitId(104L, 4L)).expense(cabRide).user(u4).amountOwed(BigDecimal.valueOf(713.33)).build();
        cabRide.setSplits(Arrays.asList(c1, c2, c3, c4));

        // Bob owed Chris exactly 554.99 — he pays 700.00 instead, overpaying by 145.01
        Settlement overpayment = Settlement.builder()
                .id(204L).group(group).fromUser(u2).toUser(u4)
                .amount(BigDecimal.valueOf(700.00)).date(LocalDateTime.now()).build();

        when(expenseRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Arrays.asList(trainRide, cabRide));
        when(settlementRepository.findByGroupIdOrderByDateDesc(10L)).thenReturn(Collections.singletonList(overpayment));

        List<BalanceDto> balances = balanceCalculationService.calculateBalances(10L);

        // Net after overpayment: Trent +4041.67, Alice -2298.33, Bob -1598.33, Chris -145.01
        // Chris flips from creditor to debtor. Bob's debt to Chris is gone entirely —
        // the excess surfaces as Chris now owing Trent, not as "Chris owes Bob".
        assertEquals(3, balances.size());

        BalanceDto aliceToTrent = findBalance(balances, 1L, 3L);
        assertNotNull(aliceToTrent);
        assertEquals(0, aliceToTrent.getAmount().compareTo(BigDecimal.valueOf(2298.33)));

        BalanceDto bobToTrent = findBalance(balances, 2L, 3L);
        assertNotNull(bobToTrent);
        assertEquals(0, bobToTrent.getAmount().compareTo(BigDecimal.valueOf(1598.33)));

        BalanceDto chrisToTrent = findBalance(balances, 4L, 3L);
        assertNotNull(chrisToTrent);
        assertEquals(0, chrisToTrent.getAmount().compareTo(BigDecimal.valueOf(145.01)));

        // Confirm the old Bob->Chris pairing is completely gone, not left over as a residual entry
        assertNull(findBalance(balances, 2L, 4L));
    }

    private BalanceDto findBalance(List<BalanceDto> balances, Long fromUserId, Long toUserId) {
        return balances.stream()
                .filter(b -> b.getFromUser().getId().equals(fromUserId) && b.getToUser().getId().equals(toUserId))
                .findFirst()
                .orElse(null);
    }
}
