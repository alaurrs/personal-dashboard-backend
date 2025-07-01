package com.dashboard.backend.service;

import com.dashboard.backend.User.model.SpotifyAccount;
import com.dashboard.backend.User.model.User;
import com.dashboard.backend.User.repository.SpotifyAccountRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
@Slf4j
public class SpotifyAccountService {

    @Autowired
    private SpotifyAccountRepository spotifyAccountRepository;

    /**
     * Vérifie si un utilisateur a un compte Spotify lié
     */
    public boolean hasSpotifyLinked(User user) {
        Optional<SpotifyAccount> account = spotifyAccountRepository.findByUser(user);
        return account.isPresent() && account.get().isLinked();
    }

    /**
     * Récupère le compte Spotify d'un utilisateur
     */
    public Optional<SpotifyAccount> getSpotifyAccount(User user) {
        return spotifyAccountRepository.findByUser(user);
    }

    /**
     * Crée ou met à jour un compte Spotify
     */
    public SpotifyAccount linkSpotifyAccount(User user, String spotifyUserId,
                                             String spotifyEmail, String displayName,
                                             String accessToken, String refreshToken,
                                             Instant tokenExpiry) {

        Optional<SpotifyAccount> existingAccount = spotifyAccountRepository.findByUser(user);

        SpotifyAccount account;
        if (existingAccount.isPresent()) {
            account = existingAccount.get();
        } else {
            account = new SpotifyAccount(user, spotifyUserId, spotifyEmail);
        }

        account.setDisplayName(displayName);
        account.setAccessToken(accessToken);
        account.setRefreshToken(refreshToken);
        account.setTokenExpiry(tokenExpiry);
        account.setLastSync(LocalDateTime.now());

        return spotifyAccountRepository.save(account);
    }

    /**
     * Met à jour les tokens d'un compte Spotify
     */
    public SpotifyAccount updateTokens(SpotifyAccount account, String accessToken,
                                       String refreshToken, Instant tokenExpiry) {
        account.setAccessToken(accessToken);
        account.setRefreshToken(refreshToken);
        account.setTokenExpiry(tokenExpiry);
        account.setLastSync(LocalDateTime.now());

        return spotifyAccountRepository.save(account);
    }

    /**
     * Dissocie un compte Spotify
     */
    @Transactional
    public void unlinkSpotifyAccount(User user) {
        log.info("Dissociation du compte Spotify pour l'utilisateur: {}", user.getEmail());

        Optional<SpotifyAccount> spotifyAccountOpt = spotifyAccountRepository.findByUser(user);

        if (spotifyAccountOpt.isPresent()) {
            SpotifyAccount spotifyAccount = spotifyAccountOpt.get();

            // Optionnel : Révoquer le token côté Spotify avant suppression
            try {
                revokeSpotifyToken(spotifyAccount.getAccessToken());
            } catch (Exception e) {
                log.warn("Impossible de révoquer le token Spotify côté serveur: {}", e.getMessage());
                // Continue quand même la suppression locale
            }

            // Supprimer l'enregistrement de la base de données
            spotifyAccountRepository.delete(spotifyAccount);
            log.info("Compte Spotify supprimé avec succès pour l'utilisateur: {}", user.getEmail());
        } else {
            log.warn("Aucun compte Spotify trouvé pour l'utilisateur: {}", user.getEmail());
        }
    }

    private void revokeSpotifyToken(String accessToken) {
        // Note: Spotify n'a pas d'endpoint de révocation officiel
        // Mais on peut "déconnecter" l'utilisateur en le redirigeant vers logout
        // Cette méthode est principalement pour le logging
        log.debug("Token Spotify marqué pour révocation (nettoyage local uniquement)");
    }


    /**
     * Vérifie si le token est expiré et doit être rafraîchi
     */
    public boolean needsTokenRefresh(SpotifyAccount account) {
        return account.isTokenExpired();
    }
}
