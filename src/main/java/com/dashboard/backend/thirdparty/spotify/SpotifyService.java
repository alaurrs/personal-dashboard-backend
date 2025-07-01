package com.dashboard.backend.thirdparty.spotify;

import com.dashboard.backend.User.model.User;
import com.dashboard.backend.User.repository.UserRepository;
import com.dashboard.backend.exception.UnauthorizedException;
import com.dashboard.backend.exception.UserNotFoundException;
import com.dashboard.backend.security.JwtService;
import com.dashboard.backend.service.SpotifyAccountService;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyProfileDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyTopArtistsDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyTopTracksDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpotifyService {

    private final SpotifyProperties spotifyProperties;
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final SpotifyClient spotifyClient;
    private final SpotifyAccountService spotifyAccountService;
    private final JwtService jwtService;

    public void exchangeCodeAndLinkUser(String code, User user) throws JsonProcessingException {
        log.info("Échange du code d'autorisation pour l'utilisateur: {}", user.getEmail());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setBasicAuth(spotifyProperties.getClientId(), spotifyProperties.getClientSecret());

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", spotifyProperties.getRedirectUri());

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(
                "https://accounts.spotify.com/api/token",
                request,
                String.class
        );

        SpotifyTokenResponseDto dto = objectMapper.readValue(response.getBody(), SpotifyTokenResponseDto.class);

        // Récupérer les infos du profil Spotify pour avoir l'email et le nom
        String spotifyUserId = null;
        String spotifyEmail = null;
        String displayName = null;

        try {
            // Utiliser temporairement le token pour récupérer les infos du profil
            SpotifyProfileDto profile = getSpotifyProfileWithToken(dto.accessToken());
            spotifyUserId = profile.id();
            spotifyEmail = profile.email();
            displayName = profile.displayName();
        } catch (Exception e) {
            log.warn("Impossible de récupérer les infos du profil Spotify, utilisation de valeurs par défaut", e);
            spotifyEmail = "unknown@spotify.com";
            displayName = "Utilisateur Spotify";
        }

        // Utiliser le service pour lier le compte Spotify
        spotifyAccountService.linkSpotifyAccount(
                user,
                spotifyUserId,
                spotifyEmail,
                displayName,
                dto.accessToken(),
                dto.refreshToken(),
                Instant.now().plusSeconds(dto.expiresIn())
        );

        log.info("Compte Spotify lié avec succès pour l'utilisateur: {}", user.getEmail());
    }


    public Optional<String> getValidAccessToken(User user) {
        return spotifyClient.getAccessToken(user);
    }

    public Optional<SpotifyProfileDto> getCurrentUserProfile(User user) {
        return spotifyClient.getCurrentProfile(user);
    }

    public Optional<SpotifyProfileDto> getCurrentUserProfileFromRequest(HttpServletRequest request) {
        String email = extractAndValidateUserFromRequest(request);
        User user = getUserByEmail(email);
        return getCurrentUserProfile(user);
    }

    public boolean hasSpotifyLinked(User user) {
        return spotifyAccountService.hasSpotifyLinked(user);
    }

    public boolean hasValidSpotifyAccountFromRequest(HttpServletRequest request) {
        try {
            String email = extractAndValidateUserFromRequest(request);
            User user = getUserByEmail(email);
            return spotifyClient.hasValidSpotifyAccount(user);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean disconnectSpotifyFromRequest(HttpServletRequest request) {
        String email = extractAndValidateUserFromRequest(request);
        User user = getUserByEmail(email);

        if (!spotifyAccountService.hasSpotifyLinked(user)) {
            log.info("Aucun compte Spotify à dissocier pour l'utilisateur: {}", email);
            return false;
        }

        spotifyAccountService.unlinkSpotifyAccount(user);
        log.info("Compte Spotify dissocié avec succès pour l'utilisateur: {}", email);
        return true;
    }

    private SpotifyProfileDto getSpotifyProfileWithToken(String accessToken) throws JsonProcessingException {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        headers.set("Accept", "application/json");

        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(
                "https://api.spotify.com/v1/me",
                org.springframework.http.HttpMethod.GET,
                request,
                String.class
        );
        return objectMapper.readValue(response.getBody(), SpotifyProfileDto.class);
    }

    private String extractAndValidateUserFromRequest(HttpServletRequest request) {
        String token = jwtService.extractTokenFromRequest(request);

        if (token == null || !jwtService.validateToken(token)) {
            throw new UnauthorizedException("Token invalide ou manquant");
        }

        String email = jwtService.extractEmail(token);
        if (email == null || email.trim().isEmpty()) {
            throw new UnauthorizedException("Email non trouvé dans le token");
        }

        return email;
    }

    private User getUserByEmail(String email) {
        User user = userRepository.findByEmail(email).orElseThrow(
                () -> new UserNotFoundException("Utilisateur non trouvé pour l'email: " + email)
        );
        return user;
    }

    public Optional<SpotifyTopArtistsDto> getTopArtists(User user, String timeRange, int limit) {
        log.debug("Récupération des top artistes pour l'utilisateur: {} (période: {})", user.getEmail(), timeRange);

        // Vérifier si l'utilisateur a un compte Spotify lié
        if (!spotifyAccountService.hasSpotifyLinked(user)) {
            log.info("Aucun compte Spotify lié pour l'utilisateur: {}", user.getEmail());
            return Optional.empty();
        }

        // Valider le timeRange
        if (!isValidTimeRange(timeRange)) {
            throw new IllegalArgumentException("Période invalide: " + timeRange + ". Valeurs autorisées: short_term, medium_term, long_term");
        }

        // Valider la limite
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("Limite invalide: " + limit + ". Doit être entre 1 et 50");
        }

        return spotifyClient.getTopArtists(user, timeRange, limit);
    }
    public Optional<SpotifyTopArtistsDto> getTopArtistsFromRequest(HttpServletRequest request, String timeRange, int limit) {
        String email = extractAndValidateUserFromRequest(request);
        User user = getUserByEmail(email);

        // MÊME validation que pour les tracks
        validateTimeRangeParameter(timeRange);
        validateLimitParameter(limit);

        return getTopArtists(user, timeRange, limit);
    }

    public Optional<SpotifyTopTracksDto> getTopTracks(User user, String timeRange, int limit) {
        log.debug("Récupération des top tracks pour l'utilisateur: {} (période: {})", user.getEmail(), timeRange);

        // Vérifier si l'utilisateur a un compte Spotify lié
        if (!spotifyAccountService.hasSpotifyLinked(user)) {
            log.info("Aucun compte Spotify lié pour l'utilisateur: {}", user.getEmail());
            return Optional.empty();
        }

        return spotifyClient.getTopTracks(user, timeRange, limit);
    }



    public Optional<SpotifyTopTracksDto> getTopTracksFromRequest(HttpServletRequest request, String timeRange, int limit) {
        // 1. Validation et extraction utilisateur
        String email = extractAndValidateUserFromRequest(request);
        User user = getUserByEmail(email);

        // 2. Validation des paramètres métier
        validateTimeRangeParameter(timeRange);
        validateLimitParameter(limit);

        // 3. Délégation à la logique métier
        return getTopTracks(user, timeRange, limit);
    }

    private void validateTimeRangeParameter(String timeRange) {
        if (!List.of("short_term", "medium_term", "long_term").contains(timeRange)) {
            throw new IllegalArgumentException(
                    "Paramètre timeRange invalide: " + timeRange +
                            ". Valeurs autorisées: short_term, medium_term, long_term"
            );
        }
    }

    /**
     * Validation du paramètre limit - logique métier centralisée
     */
    private void validateLimitParameter(int limit) {
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException(
                    "Paramètre limit invalide: " + limit +
                            ". Doit être entre 1 et 50"
            );
        }
    }


    /**
     * Valide le paramètre timeRange
     */
    private boolean isValidTimeRange(String timeRange) {
        return List.of("short_term", "medium_term", "long_term").contains(timeRange);
    }

    /**
     * Vérifie si un utilisateur a un compte Spotify valide
     */
    public boolean hasValidSpotifyAccount(User user) {
        return spotifyClient.hasValidSpotifyAccount(user);
    }
}
