package com.finance.repositories;

import com.finance.entities.RefreshToken;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Transactional
    void deleteByUser_Id(UUID userId);

    @Transactional
    void deleteByTokenHashAndUser_Id(String tokenHash, UUID userId);
}
