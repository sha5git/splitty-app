package com.expensesplit.service;

import com.expensesplit.dto.CreateSettlementRequest;
import com.expensesplit.dto.SettlementDto;
import com.expensesplit.dto.UpdateSettlementRequest;
import com.expensesplit.entity.Group;
import com.expensesplit.entity.Settlement;
import com.expensesplit.entity.User;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.repository.SettlementRepository;
import com.expensesplit.security.FirebaseUserPrincipal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class SettlementService {

    private final SettlementRepository settlementRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupService groupService;
    private final UserService userService;

    public SettlementService(SettlementRepository settlementRepository,
                             GroupMemberRepository groupMemberRepository,
                             GroupService groupService,
                             UserService userService) {
        this.settlementRepository = settlementRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.groupService = groupService;
        this.userService = userService;
    }

    @Transactional
    public SettlementDto recordSettlement(Long groupId, CreateSettlementRequest request, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());
        requireGroupMembership(groupId, currentUser.getId());
        User fromUser = resolveParty(groupId, request.getFromUserId(), "Payer (fromUser)");
        User toUser = resolveParty(groupId, request.getToUserId(), "Receiver (toUser)");
        requireDifferentParties(fromUser.getId(), toUser.getId());

        Group group = groupService.getEntityById(groupId);

        Settlement settlement = Settlement.builder()
                .group(group)
                .fromUser(fromUser)
                .toUser(toUser)
                .amount(request.getAmount())
                .date(request.getDate() != null ? request.getDate() : LocalDateTime.now())
                .build();

        settlement = settlementRepository.save(settlement);
        return convertToDto(settlement);
    }

    @Transactional
    public SettlementDto updateSettlement(Long settlementId, UpdateSettlementRequest request, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());
        Settlement settlement = findAccessibleSettlement(settlementId, currentUser.getId());
        Long groupId = settlement.getGroup().getId();

        User fromUser = resolveParty(groupId, request.getFromUserId(), "Payer (fromUser)");
        User toUser = resolveParty(groupId, request.getToUserId(), "Receiver (toUser)");
        requireDifferentParties(fromUser.getId(), toUser.getId());

        settlement.setFromUser(fromUser);
        settlement.setToUser(toUser);
        settlement.setAmount(request.getAmount());
        // Preserve original date

        return convertToDto(settlementRepository.save(settlement));
    }

    public List<SettlementDto> listSettlements(Long groupId, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());
        requireGroupMembership(groupId, currentUser.getId());

        List<Settlement> settlements = settlementRepository.findByGroupIdOrderByDateDesc(groupId);
        return settlements.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public SettlementDto convertToDto(Settlement settlement) {
        if (settlement == null) return null;
        return SettlementDto.builder()
                .id(settlement.getId())
                .groupId(settlement.getGroup().getId())
                .fromUser(userService.convertToDto(settlement.getFromUser()))
                .toUser(userService.convertToDto(settlement.getToUser()))
                .amount(settlement.getAmount())
                .date(settlement.getDate())
                .build();
    }

    private Settlement findAccessibleSettlement(Long settlementId, Long userId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Settlement not found with id: " + settlementId));
        requireGroupMembership(settlement.getGroup().getId(), userId);
        return settlement;
    }

    private void requireGroupMembership(Long groupId, Long userId) {
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, userId)) {
            throw new ResourceNotFoundException("Group not found or access denied");
        }
    }

    private User resolveParty(Long groupId, Long userId, String role) {
        User user = userService.getEntityById(userId);
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, user.getId())) {
            throw new BadRequestException(role + " is not a member of the group");
        }
        return user;
    }

    private void requireDifferentParties(Long fromUserId, Long toUserId) {
        if (fromUserId.equals(toUserId)) {
            throw new BadRequestException("Payer and recipient must be different people");
        }
    }
}
