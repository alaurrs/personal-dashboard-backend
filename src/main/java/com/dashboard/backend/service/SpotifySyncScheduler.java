package com.dashboard.backend.service;

import com.dashboard.backend.User.model.User;
import com.dashboard.backend.User.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gère les tâches planifiées liées à la synchronisation des données Spotify.
 * Ce composant est responsable de déclencher périodiquement la collecte
 * de l'historique d'écoute pour tous les utilisateurs concernés.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpotifySyncScheduler {

    private final UserRepository userRepository;
    private final SpotifyDataSyncService spotifyDataSyncService;

    /**
     * Tâche planifiée pour synchroniser l'historique d'écoute "recently-played" de Spotify.
     *
     * - `fixedRate = 1800000` : Exécute cette tâche toutes les 30 minutes (1 800 000 ms).
     *   Un intervalle court est crucial car l'API ne renvoie que les 50 dernières écoutes.
     *   Si un utilisateur écoute plus de 50 morceaux entre deux synchronisations, des données seront perdues.
     *
     * - `initialDelay = 60000` : Attend 1 minute après le démarrage de l'application avant la première exécution.
     *   Cela permet à l'application de se stabiliser complètement.
     */
    @Scheduled(initialDelay = 60000, fixedRate = 1800000)
    public void syncAllUsersListeningHistory() {
        log.info("▶️ [SCHEDULER] Démarrage de la tâche de synchronisation de l'historique Spotify pour tous les utilisateurs...");

        // 1. Récupérer tous les utilisateurs qui ont un compte Spotify lié.
        List<User> usersToSync = userRepository.findAllWithSpotifyAccount();

        if (usersToSync.isEmpty()) {
            log.info("✅ [SCHEDULER] Aucun utilisateur avec un compte Spotify lié trouvé. Tâche terminée.");
            return;
        }

        log.info("ℹ️ [SCHEDULER] {} utilisateur(s) à synchroniser.", usersToSync.size());

        int successCount = 0;
        int failureCount = 0;

        // 2. Pour chaque utilisateur, lancer la synchronisation.
        for (User user : usersToSync) {
            try {
                // Déléguer la logique complexe au service dédié.
                spotifyDataSyncService.syncRecentlyPlayedForUser(user);
                successCount++;
            } catch (Exception e) {
                // CRUCIAL : Capter les exceptions pour un utilisateur spécifique
                // afin que la tâche ne s'arrête pas pour tous les autres.
                log.error("❌ [SCHEDULER] Échec de la synchronisation pour l'utilisateur {}: {}", user.getEmail(), e.getMessage());
                failureCount++;
            }
        }

        log.info("✅ [SCHEDULER] Tâche de synchronisation terminée. Succès: {}, Échecs: {}.", successCount, failureCount);
    }
}
