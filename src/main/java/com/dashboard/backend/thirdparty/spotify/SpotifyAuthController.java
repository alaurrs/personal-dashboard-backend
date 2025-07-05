package com.dashboard.backend.thirdparty.spotify;

import com.dashboard.backend.User.model.User;
import com.dashboard.backend.User.repository.UserRepository;
import com.dashboard.backend.security.JwtService;
import com.dashboard.backend.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@RestController
@RequestMapping("/api/spotify/auth")
@RequiredArgsConstructor
@Slf4j
public class SpotifyAuthController {

    private final SpotifyProperties spotifyProperties;
    private final SpotifyService spotifyService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    @GetMapping("/login")
    public void login(
            @RequestParam("token") String jwt,
            @RequestParam(required = false, defaultValue = "http://localhost:4200/dashboard") String redirectUri,
            HttpServletResponse response) throws IOException {

        log.info("🎧 Démarrage de l'authentification Spotify pour token: {}", jwt.substring(0, 20) + "...");

        // Valider le JWT avant de rediriger
        try {
            if (!jwtService.validateToken(jwt)) {
                log.error("❌ Token JWT invalide lors de l'initialisation de l'auth Spotify");
                response.sendError(HttpStatus.UNAUTHORIZED.value(), "Token invalide");
                return;
            }
        } catch (Exception e) {
            log.error("❌ Erreur lors de la validation du token JWT", e);
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Token invalide");
            return;
        }

        String state = URLEncoder.encode(jwt + "::" + redirectUri, StandardCharsets.UTF_8);
        String url = UriComponentsBuilder.fromUriString("https://accounts.spotify.com/authorize")
                .queryParam("client_id", spotifyProperties.getClientId())
                .queryParam("response_type", "code")
                .queryParam("redirect_uri", spotifyProperties.getRedirectUri())
                .queryParam("scope", "user-read-recently-played user-top-read user-read-email user-read-private")
                .queryParam("state", state)
                .build().toUriString();

        log.debug("🔗 Redirection vers Spotify: {}", url);
        response.sendRedirect(url);
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam String code,
            @RequestParam String state
    ) {
        log.info("🎧 Callback Spotify reçu avec state = {}", state);

        try {
            // 1. Décoder et valider le state
            String[] parts = URLDecoder.decode(state, StandardCharsets.UTF_8).split("::");
            if (parts.length != 2) {
                log.warn("❌ Paramètre 'state' invalide : {}", state);
                return ResponseEntity.badRequest().build();
            }

            String token = parts[0];
            String redirect = parts[1];

            // 2. Vérifier et décoder le JWT
            String userEmail = jwtService.extractEmail(token);

            // Adaptation pour Optional<User>
            Optional<User> userOpt = userRepository.findByEmail(userEmail);
            if (userOpt.isEmpty()) {
                log.warn("❌ Utilisateur non trouvé pour l'email: {}", userEmail);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User user = userOpt.get();

            // Vérifier si l'utilisateur a déjà un compte Spotify lié
            if (spotifyService.hasSpotifyLinked(user)) {
                log.info("ℹ️ L'utilisateur {} a déjà un compte Spotify lié, mise à jour des tokens", userEmail);
            }

            // Lier le compte Spotify
            spotifyService.exchangeCodeAndLinkUser(code, user);
            log.info("✅ Compte Spotify lié/mis à jour avec succès pour l'utilisateur {}", user.getEmail());

            // Rediriger vers le frontend
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(redirect))
                    .build();

        } catch (Exception e) {
            log.error("❌ Erreur générale lors du callback Spotify", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    @GetMapping("/status")
    public ResponseEntity<?> getAuthStatus(@RequestParam("token") String jwt) {
        try {
            if (!jwtService.validateToken(jwt)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Token invalide");
            }

            String userEmail = jwtService.extractEmail(jwt);

            Optional<User> userOpt = userRepository.findByEmail(userEmail);
            if (userOpt.isEmpty()) {
                log.warn("❌ Utilisateur non trouvé pour l'email: {}", userEmail);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            User user = userOpt.get();
            boolean hasSpotifyLinked = spotifyService.hasSpotifyLinked(user);
            boolean hasValidAccount = spotifyService.hasValidSpotifyAccount(user);

            return ResponseEntity.ok()
                    .body(java.util.Map.of(
                            "hasSpotifyLinked", hasSpotifyLinked,
                            "hasValidAccount", hasValidAccount,
                            "email", userEmail
                    ));

        } catch (Exception e) {
            log.error("❌ Erreur lors de la vérification du statut Spotify", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur serveur");
        }
    }
}
