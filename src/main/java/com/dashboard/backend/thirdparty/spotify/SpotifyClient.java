package com.dashboard.backend.thirdparty.spotify;

import com.dashboard.backend.User.model.SpotifyAccount;
import com.dashboard.backend.User.model.User;
import com.dashboard.backend.User.repository.UserRepository;
import com.dashboard.backend.service.SpotifyAccountService;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyProfileDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyRecentlyPlayedDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyTopArtistsDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyTopTracksDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class SpotifyClient {

    private static final String SPOTIFY_TOKEN_URL = "https://accounts.spotify.com/api/token";
    private static final String SPOTIFY_PROFILE_URL = "https://api.spotify.com/v1/me";
    private static final String SPOTIFY_TOP_ARTISTS_URL = "https://api.spotify.com/v1/me/top/artists";

    private static final String SPOTIFY_TOP_TRACKS_URL = "https://api.spotify.com/v1/me/top/tracks";
    private static final String SPOTIFY_RECENTLY_PLAYED_URL = "https://api.spotify.com/v1/me/player/recently-played";

    private static final int TOKEN_REFRESH_BUFFER_SECONDS = 60;

    private final UserRepository userRepository;
    private final SpotifyProperties spotifyProperties;
    private final SpotifyAccountService spotifyAccountService;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public Optional<String> getAccessToken(User user) {
        log.debug("Récupération du token d'accès pour l'utilisateur: {}", user.getEmail());

        Optional<SpotifyAccount> spotifyAccountOpt = spotifyAccountService.getSpotifyAccount(user);

        if (spotifyAccountOpt.isEmpty()) {
            log.warn("Aucun compte Spotify lié pour l'utilisateur: {}", user.getEmail());
            return Optional.empty();
        }

        SpotifyAccount spotifyAccount = spotifyAccountOpt.get();

        if (isTokenExpired(spotifyAccount)) {
            log.info("Token expiré pour l'utilisateur: {}, rafraîchissement en cours", user.getEmail());
            return spotifyAccountService.refreshAccessToken(spotifyAccount);
        }

        return Optional.of(spotifyAccount.getAccessToken());
    }

    private boolean isTokenExpired(SpotifyAccount spotifyAccount) {
        if (spotifyAccount.getTokenExpiry() == null) {
            log.warn("Aucune date d'expiration définie pour le compte Spotify ID: {}", spotifyAccount.getId());
            return true;
        }

        Instant expiryWithBuffer = spotifyAccount.getTokenExpiry().minusSeconds(TOKEN_REFRESH_BUFFER_SECONDS);
        boolean expired = expiryWithBuffer.isBefore(Instant.now());

        log.debug("Token expiré: {} (expiration: {}, maintenant: {})",
                expired, spotifyAccount.getTokenExpiry(), Instant.now());

        return expired;
    }

    private Optional<String> refreshToken(SpotifyAccount spotifyAccount) {
        if (spotifyAccount.getRefreshToken() == null || spotifyAccount.getRefreshToken().isEmpty()) {
            log.error("Aucun refresh token disponible pour le compte Spotify ID: {}", spotifyAccount.getId());
            return Optional.empty();
        }

        try {
            HttpHeaders headers = createTokenRequestHeaders();
            MultiValueMap<String, String> form = createRefreshTokenForm(spotifyAccount.getRefreshToken());
            HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

            log.debug("Envoi de la requête de rafraîchissement du token à Spotify");
            ResponseEntity<String> response = restTemplate.postForEntity(SPOTIFY_TOKEN_URL, request, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                return handleTokenRefreshResponse(response.getBody(), spotifyAccount);
            } else {
                log.error("Échec du rafraîchissement du token Spotify. Status: {}", response.getStatusCode());
                return Optional.empty();
            }

        } catch (RestClientException e) {
            log.error("Erreur réseau lors du rafraîchissement du token Spotify", e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Erreur inattendue lors du rafraîchissement du token Spotify", e);
            return Optional.empty();
        }
    }

    private Optional<String> handleTokenRefreshResponse(String responseBody, SpotifyAccount spotifyAccount) {
        try {
            SpotifyTokenResponseDto tokenResponse = objectMapper.readValue(responseBody, SpotifyTokenResponseDto.class);

            Instant newExpiry = Instant.now().plusSeconds(tokenResponse.expiresIn());
            String newRefreshToken = tokenResponse.refreshToken() != null ?
                    tokenResponse.refreshToken() : spotifyAccount.getRefreshToken();

            // Utiliser le service pour mettre à jour les tokens
            spotifyAccountService.updateTokens(
                    spotifyAccount,
                    tokenResponse.accessToken(),
                    newRefreshToken,
                    newExpiry
            );

            log.info("Token Spotify rafraîchi avec succès pour le compte ID: {}", spotifyAccount.getId());
            return Optional.of(tokenResponse.accessToken());

        } catch (Exception e) {
            log.error("Erreur lors du parsing de la réponse de rafraîchissement du token", e);
            return Optional.empty();
        }
    }

    /**
     * Crée les headers pour la requête de rafraîchissement du token
     */
    private HttpHeaders createTokenRequestHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(spotifyProperties.getClientId(), spotifyProperties.getClientSecret());
        return headers;
    }

    /**
     * Crée le formulaire pour la requête de rafraîchissement du token
     */
    private MultiValueMap<String, String> createRefreshTokenForm(String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return form;
    }


    public Optional<SpotifyProfileDto> getCurrentProfile(User user) {
        log.debug("Récupération du profil Spotify pour l'utilisateur: {}", user.getEmail());

        Optional<String> tokenOpt = getAccessToken(user);
        if (tokenOpt.isEmpty()) {
            log.warn("Impossible de récupérer un token valide pour l'utilisateur: {}", user.getEmail());
            return Optional.empty();
        }

        try {
            HttpHeaders headers = createApiRequestHeaders(tokenOpt.get());
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    SPOTIFY_PROFILE_URL,
                    HttpMethod.GET,
                    request,
                    String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                SpotifyProfileDto profile = objectMapper.readValue(response.getBody(), SpotifyProfileDto.class);
                log.debug("Profil Spotify récupéré avec succès pour l'utilisateur: {}", user.getEmail());
                return Optional.of(profile);
            } else {
                log.error("Échec de la récupération du profil Spotify. Status: {}", response.getStatusCode());
                return Optional.empty();
            }

        } catch (RestClientException e) {
            log.error("Erreur réseau lors de la récupération du profil Spotify pour l'utilisateur: {}", user.getEmail(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Erreur lors de la récupération du profil Spotify pour l'utilisateur: {}", user.getEmail(), e);
            return Optional.empty();
        }
    }

    public Optional<SpotifyTopArtistsDto> getTopArtists(User user, String timeRange, int limit) {
        log.debug("Récupération des top artistes pour l'utilisateur: {} (période: {}, limite: {})",
                user.getEmail(), timeRange, limit);

        Optional<String> tokenOpt = getAccessToken(user);
        if (tokenOpt.isEmpty()) {
            log.warn("Impossible de récupérer un token valide pour l'utilisateur: {}", user.getEmail());
            return Optional.empty();
        }

        return makeSpotifyApiCall(
                buildTopArtistsUrl(timeRange, limit),
                tokenOpt.get(),
                SpotifyTopArtistsDto.class
        );
    }

    public Optional<SpotifyTopTracksDto> getTopTracks(User user, String timeRange, int limit) {
        log.debug("Récupération des top tracks pour l'utilisateur: {} (période: {}, limite: {})",
                user.getEmail(), timeRange, limit);

        Optional<String> tokenOpt = getAccessToken(user);
        if (tokenOpt.isEmpty()) {
            log.warn("Impossible de récupérer un token valide pour l'utilisateur: {}", user.getEmail());
            return Optional.empty();
        }

        return makeSpotifyApiCall(
                buildTopTracksUrl(timeRange, limit),
                tokenOpt.get(),
                SpotifyTopTracksDto.class
        );
    }

    private String buildTopTracksUrl(String timeRange, int limit) {
        return SPOTIFY_TOP_TRACKS_URL +
                "?time_range=" + timeRange +
                "&limit=" + limit;
    }


    private String buildTopArtistsUrl(String timeRange, int limit) {
        return "https://api.spotify.com/v1/me/top/artists" +
                "?time_range=" + timeRange +
                "&limit=" + limit;
    }

    private <T> Optional<T> makeSpotifyApiCall(String url, String accessToken, Class<T> responseType) {
        try {
            HttpHeaders headers = createApiRequestHeaders(accessToken);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, String.class
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                T result = objectMapper.readValue(response.getBody(), responseType);
                return Optional.of(result);
            } else {
                log.error("Échec de l'appel API Spotify. Status: {}", response.getStatusCode());
                return Optional.empty();
            }

        } catch (Exception e) {
            log.error("Erreur lors de l'appel API Spotify: {}", url, e);
            return Optional.empty();
        }
    }



    /**
     * Crée les headers pour les requêtes API Spotify
     */
    private HttpHeaders createApiRequestHeaders(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return headers;
    }

    public boolean hasValidSpotifyAccount(User user) {
        Optional<SpotifyAccount> spotifyAccountOpt = spotifyAccountService.getSpotifyAccount(user);

        if (spotifyAccountOpt.isEmpty()) {
            return false;
        }

        SpotifyAccount spotifyAccount = spotifyAccountOpt.get();
        return spotifyAccount.isLinked() &&
                (spotifyAccount.getRefreshToken() != null || !isTokenExpired(spotifyAccount));
    }

    public void revokeSpotifyAccess(User user) {
        log.info("Révocation de l'accès Spotify pour l'utilisateur: {}", user.getEmail());
        spotifyAccountService.unlinkSpotifyAccount(user);
    }

    public Optional<SpotifyRecentlyPlayedDto> getRecentlyPlayed(User user) {
        return getRecentlyPlayed(user, null);
    }

    public Optional<SpotifyRecentlyPlayedDto> getRecentlyPlayed(User user, Instant after) {
        log.debug("Récupération de l'historique d'écoute pour l'utilisateur: {}", user.getEmail());

        // 1. Récupère un token valide (gère le refresh automatiquement)
        Optional<String> tokenOpt = getAccessToken(user);
        if (tokenOpt.isEmpty()) {
            log.warn("Impossible de récupérer un token valide pour la synchronisation de l'historique de {}.", user.getEmail());
            return Optional.empty();
        }

        // 2. Construire l'URL de l'API avec le paramètre 'after' si fourni
        StringBuilder urlBuilder = new StringBuilder(SPOTIFY_RECENTLY_PLAYED_URL + "?limit=50");
        if (after != null) {
            // Convertir l'Instant en timestamp Unix en millisecondes
            long afterTimestamp = after.toEpochMilli();
            urlBuilder.append("&after=").append(afterTimestamp);
            log.debug("Récupération des morceaux après le timestamp: {} ({})", afterTimestamp, after);
        }

        // 3. Faire l'appel API en utilisant la méthode générique
        return makeSpotifyApiCall(urlBuilder.toString(), tokenOpt.get(), SpotifyRecentlyPlayedDto.class);
    }
}
