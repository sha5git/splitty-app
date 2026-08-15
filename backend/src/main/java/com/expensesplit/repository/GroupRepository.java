package com.expensesplit.repository;

import com.expensesplit.entity.Group;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface GroupRepository extends JpaRepository<Group, Long> {
    @Query("SELECT gm.group FROM GroupMember gm WHERE gm.user.id = :userId ORDER BY gm.group.createdAt DESC")
    List<Group> findGroupsByUserId(@Param("userId") Long userId);
}
