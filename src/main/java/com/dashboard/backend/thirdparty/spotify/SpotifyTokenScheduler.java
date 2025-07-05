package com.dashboard.backend.thirdparty.spotify;

import com.dashboard.backend.User.model.SpotifyAccount;
import com.dashboard.backend.User.repository.SpotifyAccountRepository;
import com.dashboard.backend.service.SpotifyAccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpotifyTokenScheduler {

    private final SpotifyAccountRepository spotifyAccountRepository;
    private final SpotifyAccountService spotifyAccountService;

    // Exécute toutes les 30 minutes (1800000 millisecondes)
    @Scheduled(fixedRate = 1800000)
    public void refreshExpiringSpotifyTokens() {
        log.info("▶️ Démarrage de la tâche de rafraîchissement des tokens Spotify...");

        // On cherche les tokens qui vont expirer dans les 15 prochaines minutes (900 secondes)
        Instant expiryThreshold = Instant.now().plusSeconds(900);
        List<SpotifyAccount> accountsToRefresh = spotifyAccountRepository.findByTokenExpiryBefore(expiryThreshold);

        if (accountsToRefresh.isEmpty()) {
            log.info("✅ Aucun token Spotify à rafraîchir.");
            return;
        }

        log.info("Found {} comptes Spotify avec des tokens expirant bientôt.", accountsToRefresh.size());

        int successCount = 0;
        for (SpotifyAccount account : accountsToRefresh) {
            try {
                // On utilise la méthode centralisée
                spotifyAccountService.refreshAccessToken(account);
                successCount++;
            } catch (Exception e) {
                log.error("❌ Échec du rafraîchissement programmé pour le compte Spotify ID: {}", account.getId(), e);
            }
        }
        log.info("✅ Tâche de rafraîchissement terminée. {}/{} tokens rafraîchis.", successCount, accountsToRefresh.size());
    }
}