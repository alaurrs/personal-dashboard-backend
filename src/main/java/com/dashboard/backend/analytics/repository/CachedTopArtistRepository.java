package com.dashboard.backend.analytics.repository;

import com.dashboard.backend.analytics.model.CachedTopArtist;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CachedTopArtistRepository extends JpaRepository<CachedTopArtist, UUID> {

    /**
     * Méthode clé pour vérifier la fraîcheur du cache.
     * Elle trouve la date de la dernière mise à jour pour un utilisateur et une période donnés.
     */
    @Query("SELECT MAX(c.lastUpdatedAt) FROM CachedTopArtist c WHERE c.user.id = :userId AND c.timeRange = :timeRange")
    Optional<Instant> findLastUpdateForUserAndTimeRange(@Param("userId") UUID userId, @Param("timeRange") String timeRange);

    /**
     * Récupère les artistes du cache, triés par leur classement.
     */
    List<CachedTopArtist> findByUser_IdAndTimeRangeOrderByRankAsc(UUID userId, String timeRange);

    /**
     * Vide le cache pour un utilisateur et une période avant d'y insérer de nouvelles données.
     */
    @Modifying
    void deleteByUser_IdAndTimeRange(UUID userId, String timeRange);
}
