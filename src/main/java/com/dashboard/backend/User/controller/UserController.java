package com.dashboard.backend.User.controller;

import com.dashboard.backend.User.dto.UserDto;
import com.dashboard.backend.User.model.SpotifyAccount;
import com.dashboard.backend.User.model.User;
import com.dashboard.backend.User.repository.UserRepository;
import com.dashboard.backend.security.JwtService;
import com.dashboard.backend.security.UserPrincipal;
import com.dashboard.backend.service.SpotifyAccountService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/user")
@CrossOrigin(origins = "http://localhost:4200")
@AllArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private SpotifyAccountService spotifyAccountService;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(HttpServletRequest request) {
        try {
            String token = jwtService.extractTokenFromRequest(request);

            if (token == null || !jwtService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token invalide");
            }

            String email = jwtService.extractEmail(token);
            User user = userRepository.findByEmail(email).orElseThrow(
                    () -> new RuntimeException("Utilisateur non trouvé")
            );

            UserDto userDto = UserDto.from(user);

            return ResponseEntity.ok(userDto);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Erreur serveur");
        }
    }


    @GetMapping("/me/spotify-status")
    public ResponseEntity<Map<String, Object>> getSpotifyStatus(HttpServletRequest request) {
        try {
            String token = jwtService.extractTokenFromRequest(request);

            if (token == null || !jwtService.validateToken(token)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String email = jwtService.extractEmail(token);
            User user = userRepository.findByEmail(email).orElseThrow(
                    () -> new RuntimeException("Utilisateur non trouvé")
            );

            // Utilisation du service dédié pour Spotify
            boolean hasSpotifyLinked = spotifyAccountService.hasSpotifyLinked(user);
            String spotifyEmail = null;
            String displayName = null;

            if (hasSpotifyLinked) {
                Optional<SpotifyAccount> spotifyAccount = spotifyAccountService.getSpotifyAccount(user);
                if (spotifyAccount.isPresent()) {
                    spotifyEmail = spotifyAccount.get().getSpotifyEmail();
                    displayName = spotifyAccount.get().getDisplayName();
                }
            }

            Map<String, Object> response = new HashMap<>();
            response.put("hasSpotifyLinked", hasSpotifyLinked);
            response.put("spotifyEmail", spotifyEmail);
            response.put("displayName", displayName);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
    }
}
