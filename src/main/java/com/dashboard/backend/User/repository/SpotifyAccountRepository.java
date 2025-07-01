package com.dashboard.backend.User.repository;

import com.dashboard.backend.User.model.SpotifyAccount;
import com.dashboard.backend.User.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SpotifyAccountRepository extends JpaRepository<SpotifyAccount, UUID> {

    Optional<SpotifyAccount> findByUser(User user);

    Optional<SpotifyAccount> findByUserId(UUID userId);

    Optional<SpotifyAccount> findBySpotifyUserId(String spotifyUserId);

    boolean existsByUser(User user);

    void deleteByUser(User user);
}
