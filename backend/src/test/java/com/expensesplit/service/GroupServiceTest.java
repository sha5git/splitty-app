package com.expensesplit.service;

import com.expensesplit.dto.GroupDto;
import com.expensesplit.dto.UpdateGroupRequest;
import com.expensesplit.dto.UserDto;
import com.expensesplit.entity.Group;
import com.expensesplit.entity.User;
import com.expensesplit.exception.ResourceNotFoundException;
import com.expensesplit.repository.GroupMemberRepository;
import com.expensesplit.repository.GroupRepository;
import com.expensesplit.repository.UserRepository;
import com.expensesplit.security.FirebaseUserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class GroupServiceTest {

    @InjectMocks
    private GroupService groupService;

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private GroupMemberRepository groupMemberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserService userService;

    private User u1;
    private Group group;
    private FirebaseUserPrincipal principal;

    @BeforeEach
    public void setup() {
        u1 = User.builder().id(1L).firebaseUid("uid1").name("User 1").email("u1@example.com").build();
        group = Group.builder()
                .id(10L)
                .name("Old name")
                .createdBy(u1)
                .createdAt(LocalDateTime.of(2026, 1, 1, 12, 0))
                .build();
        principal = new FirebaseUserPrincipal("uid1", "u1@example.com", "User 1", null);

        org.mockito.Mockito.lenient().when(userService.convertToDto(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return UserDto.builder()
                    .id(user.getId())
                    .firebaseUid(user.getFirebaseUid())
                    .name(user.getName())
                    .email(user.getEmail())
                    .build();
        });
        org.mockito.Mockito.lenient().when(groupMemberRepository.findByGroupId(10L)).thenReturn(Collections.emptyList());
    }

    @Test
    public void updateGroup_renamesGroup_whenCallerIsMember() {
        when(userService.getEntityByFirebaseUid("uid1")).thenReturn(u1);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 1L)).thenReturn(true);
        when(groupRepository.findById(10L)).thenReturn(Optional.of(group));
        when(groupRepository.save(any(Group.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateGroupRequest request = new UpdateGroupRequest("  Weekend in Goa  ");
        GroupDto dto = groupService.updateGroup(10L, request, principal);

        assertEquals("Weekend in Goa", dto.getName());
        assertEquals("Weekend in Goa", group.getName());
        verify(groupRepository).save(group);
    }

    @Test
    public void updateGroup_throwsNotFound_whenCallerNotInGroup() {
        when(userService.getEntityByFirebaseUid("uid1")).thenReturn(u1);
        when(groupMemberRepository.existsByIdGroupIdAndIdUserId(10L, 1L)).thenReturn(false);

        UpdateGroupRequest request = new UpdateGroupRequest("New name");

        assertThrows(ResourceNotFoundException.class, () -> groupService.updateGroup(10L, request, principal));
        verify(groupRepository, never()).save(any());
    }
}
