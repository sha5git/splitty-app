package com.expensesplit.service;

import com.expensesplit.dto.GroupDto;
import com.expensesplit.dto.UserDto;
import com.expensesplit.dto.CreateGroupRequest;
import com.expensesplit.dto.AddMemberRequest;
import com.expensesplit.entity.Group;
import com.expensesplit.entity.GroupMember;
import com.expensesplit.entity.GroupMemberId;
import com.expensesplit.entity.User;
import com.expensesplit.exception.BadRequestException;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.repository.GroupRepository;
import com.expensesplit.repository.UserRepository;
import com.expensesplit.security.FirebaseUserPrincipal;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final UserRepository userRepository;
    private final UserService userService;

    public GroupService(GroupRepository groupRepository,
                        GroupMemberRepository groupMemberRepository,
                        UserRepository userRepository,
                        UserService userService) {
        this.groupRepository = groupRepository;
        this.groupMemberRepository = groupMemberRepository;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Transactional
    public GroupDto createGroup(CreateGroupRequest request, FirebaseUserPrincipal principal) {
        User creator = userService.getEntityByFirebaseUid(principal.getUid());

        Group group = Group.builder()
                .name(request.getName())
                .createdBy(creator)
                .build();
        group = groupRepository.save(group);

        // Add creator as first group member
        GroupMemberId memberId = new GroupMemberId(group.getId(), creator.getId());
        GroupMember member = GroupMember.builder()
                .id(memberId)
                .group(group)
                .user(creator)
                .joinedAt(LocalDateTime.now())
                .build();
        groupMemberRepository.save(member);

        return convertToDto(group);
    }

    public List<GroupDto> listGroups(FirebaseUserPrincipal principal) {
        User user = userService.getEntityByFirebaseUid(principal.getUid());
        List<Group> groups = groupRepository.findGroupsByUserId(user.getId());
        return groups.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    public GroupDto getGroupDetails(Long groupId, FirebaseUserPrincipal principal) {
        User user = userService.getEntityByFirebaseUid(principal.getUid());
        
        // Security check: must be a member to see details
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, user.getId())) {
            throw new ResourceNotFoundException("Group not found or access denied");
        }

        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + groupId));

        return convertToDto(group);
    }

    public Group getEntityById(Long id) {
        return groupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found with id: " + id));
    }

    @Transactional
    public void addMember(Long groupId, AddMemberRequest request, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());
        
        // Security check
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, currentUser.getId())) {
            throw new ResourceNotFoundException("Group not found or access denied");
        }

        Group group = getEntityById(groupId);
        User userToAdd;

        if (request.getUserId() != null) {
            userToAdd = userService.getEntityById(request.getUserId());
        } else if (request.getEmail() != null) {
            userToAdd = userRepository.findByEmail(request.getEmail())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found with email: " + request.getEmail()));
        } else {
            throw new BadRequestException("Must provide either userId or email");
        }

        GroupMemberId memberId = new GroupMemberId(group.getId(), userToAdd.getId());
        if (groupMemberRepository.existsById(memberId)) {
            throw new BadRequestException("User is already a member of this group");
        }

        GroupMember newMember = GroupMember.builder()
                .id(memberId)
                .group(group)
                .user(userToAdd)
                .joinedAt(LocalDateTime.now())
                .build();
        groupMemberRepository.save(newMember);
    }

    @Transactional
    public void removeMember(Long groupId, Long userId, FirebaseUserPrincipal principal) {
        User currentUser = userService.getEntityByFirebaseUid(principal.getUid());

        // Security check
        if (!groupMemberRepository.existsByIdGroupIdAndIdUserId(groupId, currentUser.getId())) {
            throw new ResourceNotFoundException("Group not found or access denied");
        }

        GroupMemberId memberId = new GroupMemberId(groupId, userId);
        if (!groupMemberRepository.existsById(memberId)) {
            throw new ResourceNotFoundException("Member not found in this group");
        }

        groupMemberRepository.deleteById(memberId);
    }

    public GroupDto convertToDto(Group group) {
        if (group == null) return null;

        List<UserDto> members = groupMemberRepository.findByGroupId(group.getId())
                .stream()
                .map(gm -> userService.convertToDto(gm.getUser()))
                .collect(Collectors.toList());

        return GroupDto.builder()
                .id(group.getId())
                .name(group.getName())
                .createdBy(userService.convertToDto(group.getCreatedBy()))
                .createdAt(group.getCreatedAt())
                .members(members)
                .build();
    }
}
