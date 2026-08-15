package com.expensesplit.controller;

import com.expensesplit.dto.AddMemberRequest;
import com.expensesplit.dto.CreateGroupRequest;
import com.expensesplit.dto.GroupDto;
import com.expensesplit.security.FirebaseUserPrincipal;
import com.expensesplit.service.GroupService;
import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupDto createGroup(@Valid @RequestBody CreateGroupRequest request,
                                 @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        return groupService.createGroup(request, principal);
    }

    @GetMapping
    public List<GroupDto> listGroups(@AuthenticationPrincipal FirebaseUserPrincipal principal) {
        return groupService.listGroups(principal);
    }

    @GetMapping("/{id}")
    public GroupDto getGroupDetails(@PathVariable("id") Long id,
                                    @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        return groupService.getGroupDetails(id, principal);
    }

    @PostMapping("/{id}/members")
    @ResponseStatus(HttpStatus.OK)
    public void addMember(@PathVariable("id") Long id,
                          @RequestBody AddMemberRequest request,
                          @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        groupService.addMember(id, request, principal);
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.OK)
    public void removeMember(@PathVariable("id") Long id,
                             @PathVariable("userId") Long userId,
                             @AuthenticationPrincipal FirebaseUserPrincipal principal) {
        groupService.removeMember(id, userId, principal);
    }
}
