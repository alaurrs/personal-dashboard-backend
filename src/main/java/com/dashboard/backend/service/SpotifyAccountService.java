package com.dashboard.backend.service;

import com.dashboard.backend.User.model.SpotifyAccount;
import com.dashboard.backend.User.model.User;
import com.dashboard.backend.User.repository.SpotifyAccountRepository;
import com.dashboard.backend.thirdparty.spotify.SpotifyProperties;
import com.dashboard.backend.thirdparty.spotify.SpotifyTokenResponseDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Transactional
@RequiredArgsConstructor
@Slf4j
public class SpotifyAccountService {

    private final SpotifyAccountRepository spotifyAccountRepository;
    private final SpotifyProperties spotifyProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

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
        account = existingAccount.orElseGet(() -> new SpotifyAccount(user, spotifyUserId, spotifyEmail));

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

    public Optional<String> refreshAccessToken(SpotifyAccount account) {
        log.info("Tentative de rafraîchissement du token pour le compte Spotify ID: {}", account.getId());

        if (account.getRefreshToken() == null || account.getRefreshToken().isEmpty()) {
            log.error("Aucun refresh token disponible pour le compte Spotify ID: {}. Impossible de rafraîchir.", account.getId());
            return Optional.empty();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(spotifyProperties.getClientId(), spotifyProperties.getClientSecret());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", account.getRefreshToken());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "https://accounts.spotify.com/api/token",
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                SpotifyTokenResponseDto tokenResponse = objectMapper.readValue(response.getBody(), SpotifyTokenResponseDto.class);

                // Mettre à jour les tokens dans la base de données
                updateTokens(
                        account,
                        tokenResponse.accessToken(),
                        // Spotify peut parfois retourner un nouveau refresh_token
                        tokenResponse.refreshToken() != null ? tokenResponse.refreshToken() : account.getRefreshToken(),
                        Instant.now().plusSeconds(tokenResponse.expiresIn())
                );

                log.info("Token Spotify rafraîchi avec succès pour le compte ID: {}", account.getId());
                return Optional.of(tokenResponse.accessToken());
            } else {
                log.error("Échec du rafraîchissement du token Spotify. Statut: {}", response.getStatusCode());
                return Optional.empty();
            }
        } catch (Exception e) {
            log.error("Erreur critique lors du rafraîchissement du token Spotify pour le compte ID: {}. L'utilisateur devra peut-être se reconnecter.", account.getId(), e);
            // Ici, on pourrait marquer le compte comme invalide si l'erreur persiste (ex: token révoqué)
            return Optional.empty();
        }
    }
}
