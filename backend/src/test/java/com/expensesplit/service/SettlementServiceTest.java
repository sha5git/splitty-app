package com.expensesplit.service;

import com.expensesplit.dto.SettlementDto;
import com.expensesplit.dto.UpdateSettlementRequest;
import com.expensesplit.dto.UserDto;
import com.expensesplit.entity.Group;
import com.expensesplit.entity.Settlement;
import com.expensesplit.entity.User;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.repository.SettlementRepository;
import com.expensesplit.security.FirebaseUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SettlementServiceTest {

    @InjectMocks
    private SettlementService settlementService;

    @Mock
    private SettlementRepository settlementRepository;

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
    private Settlement settlement;

    @BeforeEach
    public void setup() {
        u1 = User.builder().id(1L).firebaseUid("uid1").name("Alice").email("a@example.com").build();
        u2 = User.builder().id(2L).firebaseUid("uid2").name("Trent").email("t@example.com").build();
        group = Group.builder().id(10L).name("Trip").createdBy(u1).build();
        principal = new FirebaseUserPrincipal("uid1", "a@example.com", "Alice", null);
        originalDate = LocalDateTime.of(2026, 8, 23, 1, 0, 0);
        settlement = Settlement.builder()
                .id(50L)
                .group(group)
                .fromUser(u1)
                .toUser(u2)
                .amount(BigDecimal.valueOf(2500.00))
                .date(originalDate)
                .build();

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
    public void updateSettlement_updatesPartiesAndAmount_andPreservesDate() {
        when(userService.getEntityByFirebaseUid("uid1")).thenReturn(u1);
        when(settlementRepository.findById(50L)).thenReturn(Optional.of(settlement));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 1L)).thenReturn(true);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 2L)).thenReturn(true);
        when(userService.getEntityById(1L)).thenReturn(u1);
        when(userService.getEntityById(2L)).thenReturn(u2);
        when(settlementRepository.save(any(Settlement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateSettlementRequest request = new UpdateSettlementRequest();
        request.setFromUserId(1L);
        request.setToUserId(2L);
        request.setAmount(BigDecimal.valueOf(250.00));

        SettlementDto dto = settlementService.updateSettlement(50L, request, principal);

        assertEquals(50L, dto.getId());
        assertEquals(1L, dto.getFromUser().getId());
        assertEquals(2L, dto.getToUser().getId());
        assertEquals(0, dto.getAmount().compareTo(BigDecimal.valueOf(250.00)));
        assertEquals(originalDate, dto.getDate());
        verify(settlementRepository).save(settlement);
    }

    @Test
    public void updateSettlement_throwsNotFound_whenCallerNotInGroup() {
        when(userService.getEntityByFirebaseUid("uid1")).thenReturn(u1);
        when(settlementRepository.findById(50L)).thenReturn(Optional.of(settlement));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 1L)).thenReturn(false);

        UpdateSettlementRequest request = new UpdateSettlementRequest();
        request.setFromUserId(1L);
        request.setToUserId(2L);
        request.setAmount(BigDecimal.valueOf(250.00));

        assertThrows(ResourceNotFoundException.class,
                () -> settlementService.updateSettlement(50L, request, principal));
        verify(settlementRepository, never()).save(any());
    }

    @Test
    public void updateSettlement_throwsBadRequest_whenSamePerson() {
        when(userService.getEntityByFirebaseUid("uid1")).thenReturn(u1);
        when(settlementRepository.findById(50L)).thenReturn(Optional.of(settlement));
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 1L)).thenReturn(true);
        when(userService.getEntityById(1L)).thenReturn(u1);

        UpdateSettlementRequest request = new UpdateSettlementRequest();
        request.setFromUserId(1L);
        request.setToUserId(1L);
        request.setAmount(BigDecimal.valueOf(250.00));

        assertThrows(BadRequestException.class,
                () -> settlementService.updateSettlement(50L, request, principal));
        verify(settlementRepository, never()).save(any());
    }
}
