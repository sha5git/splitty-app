package com.expensesplit.repository;

import com.expensesplit.entity.GroupMember;
import com.expensesplit.entity.GroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface GroupMemberRepository extends JpaRepository<GroupMember, GroupMemberId> {
    List<GroupMember> findByGroupId(Long groupId);
    boolean existsByIdGroupIdAndIdUserId(Long groupId, Long userId);
}
