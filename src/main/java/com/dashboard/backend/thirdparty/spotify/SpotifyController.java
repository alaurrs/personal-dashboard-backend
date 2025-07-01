package com.dashboard.backend.thirdparty.spotify;

import com.dashboard.backend.exception.SpotifyAccountNotLinkedException;
import com.dashboard.backend.exception.UnauthorizedException;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyProfileDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyTopArtistsDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyTopTracksDto;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/spotify")
@RequiredArgsConstructor
@Slf4j
public class SpotifyController {

    private final SpotifyService spotifyService;

    @GetMapping("/me")
    public ResponseEntity<SpotifyProfileDto> getSpotifyProfile(HttpServletRequest request) {
        Optional<SpotifyProfileDto> profileOpt = spotifyService.getCurrentUserProfileFromRequest(request);
        return ResponseEntity.ok(profileOpt.orElseThrow(() ->
            new SpotifyAccountNotLinkedException("Aucun compte Spotify lié ou erreur de récupération")));
    }

    @GetMapping("/status")
    public ResponseEntity<java.util.Map<String, Boolean>> getSpotifyStatus(HttpServletRequest request) {
        boolean hasValidAccount = spotifyService.hasValidSpotifyAccountFromRequest(request);
        return ResponseEntity.ok(java.util.Map.of("hasSpotifyLinked", hasValidAccount));
    }

    @GetMapping("/top-artists")
    public ResponseEntity<SpotifyTopArtistsDto> getTopArtists(
            HttpServletRequest request,
            @RequestParam(defaultValue = "medium_term") String timeRange,
            @RequestParam(defaultValue = "20") int limit) {

        // Valider les paramètres
        if (!List.of("short_term", "medium_term", "long_term").contains(timeRange)) {
            throw new IllegalArgumentException("Paramètre timeRange invalide. Valeurs autorisées: short_term, medium_term, long_term");
        }

        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("Paramètre limit invalide. Doit être entre 1 et 50");
        }

        Optional<SpotifyTopArtistsDto> topArtistsOpt = spotifyService.getTopArtistsFromRequest(request, timeRange, limit);
        return ResponseEntity.ok(topArtistsOpt.orElseThrow(() ->
            new SpotifyAccountNotLinkedException("Impossible de récupérer les top artistes")));
    }

    @DeleteMapping("/disconnect")
    public ResponseEntity<?> disconnectSpotify(HttpServletRequest request) {
        try {
            boolean success = spotifyService.disconnectSpotifyFromRequest(request);

            return success
                    ? ResponseEntity.ok().body("Compte Spotify dissocié avec succès")
                    : ResponseEntity.status(HttpStatus.NOT_FOUND).body("Aucun compte Spotify trouvé");

        } catch (UnauthorizedException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(e.getMessage());
        } catch (Exception e) {
            log.error("Erreur lors de la dissociation du compte Spotify", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Erreur serveur");
        }
    }

    @GetMapping("/top-tracks")
    public ResponseEntity<SpotifyTopTracksDto> getTopTracks(
            HttpServletRequest request,
            @RequestParam(defaultValue = "medium_term") String timeRange,
            @RequestParam(defaultValue = "20") int limit) {

        // MÊME PATTERN que top-artists - délégation pure
        Optional<SpotifyTopTracksDto> topTracksOpt = spotifyService.getTopTracksFromRequest(request, timeRange, limit);
        return ResponseEntity.ok(topTracksOpt.orElseThrow(() ->
                new SpotifyAccountNotLinkedException("Impossible de récupérer les top tracks")));
    }
}
