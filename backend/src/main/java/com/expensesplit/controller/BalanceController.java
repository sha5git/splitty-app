package com.expensesplit.controller;

import com.expensesplit.dto.BalanceDto;
import com.expensesplit.dto.GroupNetBalanceDto;
import com.expensesplit.security.FirebaseUserPrincipal;
import com.expensesplit.service.BalanceCalculationService;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.service.UserService;
import com.expensesplit.entity.User;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups/{id}")
public class BalanceController {

    private final BalanceCalculationService balanceCalculationService;
    private final GroupMemberRepository groupMemberRepository;
    private final UserService userService;

    public BalanceController(BalanceCalculationService balanceCalculationService,
                             GroupMemberRepository groupMemberRepository,
                             UserService userService) {
        this.balanceCalculationService = balanceCalculationService;
        this.groupMemberRepository = groupMemberRepository;
        this.userService = userService;
    }

    @GetMapping("/balances")
    public List<BalanceDto> getBalances(@PathVariable("id") Long groupId,
                                        @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        requireGroupMember(groupId, principal);
        return balanceCalculationService.calculateBalances(groupId);
    }

    @GetMapping("/net-balance")
    public GroupNetBalanceDto getNetBalance(@PathVariable("id") Long groupId,
                                            @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        User currentUser = requireGroupMember(groupId, principal);

        return GroupNetBalanceDto.builder()
                .groupId(groupId)
                .user(userService.convertToDto(currentUser))
                .netBalance(balanceCalculationService.calculateUserNetBalance(groupId, currentUser.getId()))
                .build();
    }

    private User requireGroupMember(Long groupId, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());

        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, currentUser.getId())) {
            throw new ResourceNotFoundException("Group not found or access denied");
        }

        return currentUser;
    }
}
