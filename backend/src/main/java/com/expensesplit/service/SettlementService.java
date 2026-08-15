package com.expensesplit.service;

import com.expensesplit.dto.CreateSettlementRequest;
import com.expensesplit.dto.SettlementDto;
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

        // Security check: current user must be in the group
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, currentUser.getId())) {
            throw new ResourceNotFoundException("Group not found or access denied");
        }

        Group group = groupService.getEntityById(groupId);

        User fromUser = userService.getEntityById(request.getFromUserId());
        User toUser = userService.getEntityById(request.getToUserId());

        // Both must be members
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, fromUser.getId())) {
            throw new BadRequestException("Payer (fromUser) is not a member of the group");
        }
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, toUser.getId())) {
            throw new BadRequestException("Receiver (toUser) is not a member of the group");
        }

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

    public List<SettlementDto> listSettlements(Long groupId, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());

        // Security check
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, currentUser.getId())) {
            throw new ResourceNotFoundException("Group not found or access denied");
        }

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
}
