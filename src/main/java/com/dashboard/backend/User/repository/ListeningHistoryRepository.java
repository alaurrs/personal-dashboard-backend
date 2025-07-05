package com.dashboard.backend.User.repository;

import com.dashboard.backend.User.model.ListeningHistory;
import com.dashboard.backend.User.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ListeningHistoryRepository extends JpaRepository<ListeningHistory, UUID> {

    boolean existsByUserAndPlayedAt(User user, Instant playedAt);
    Optional<ListeningHistory> findTopByUserOrderByPlayedAtDesc(User user);
    List<ListeningHistory> findByUserOrderByPlayedAtDesc(User user);

    long countByUser(User user);
    List<ListeningHistory> findByUserAndPlayedAtBetween(User user, Instant startDate, Instant endDate);
}
