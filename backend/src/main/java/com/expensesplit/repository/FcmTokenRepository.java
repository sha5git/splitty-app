package com.expensesplit.repository;

import com.expensesplit.entity.FcmToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {
    Optional<FcmToken> findByToken(String token);

    List<FcmToken> findByUserIdIn(Collection<Long> userIds);

    void deleteByTokenAndUserId(String token, Long userId);

    void deleteByToken(String token);
}
