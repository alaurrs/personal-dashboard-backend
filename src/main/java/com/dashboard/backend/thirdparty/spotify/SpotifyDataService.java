package com.dashboard.backend.thirdparty.spotify;

import com.dashboard.backend.User.model.User;
import com.dashboard.backend.service.SpotifyAccountService;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyProfileDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyTopArtistsDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyTopTracksDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpotifyDataService {

    private final SpotifyClient spotifyClient;
    private final SpotifyAccountService spotifyAccountService;

    @Cacheable(value = "spotifyProfile", key = "#user.id.toString()")
    public Optional<SpotifyProfileDto> getCurrentProfile(User user) {
        log.debug("CACHE MISS: Récupération du profil Spotify pour l'utilisateur: {}", user.getEmail());
        return spotifyClient.getCurrentProfile(user);
    }

    @Cacheable(value = "spotifyTopArtists", key = "#user.id.toString() + '-' + #timeRange + '-' + #limit")
    public Optional<SpotifyTopArtistsDto> getTopArtists(User user, String timeRange, int limit) {
        log.debug("CACHE MISS: Récupération des top artistes pour l'utilisateur: {}", user.getEmail());
        if (!spotifyAccountService.hasSpotifyLinked(user)) { return Optional.empty(); }
        return spotifyClient.getTopArtists(user, timeRange, limit);
    }

    @Cacheable(value = "spotifyTopTracks", key = "#user.id.toString() + '-' + #timeRange + '-' + #limit")
    public Optional<SpotifyTopTracksDto> getTopTracks(User user, String timeRange, int limit) {
        log.debug("CACHE MISS: Récupération des top tracks pour l'utilisateur: {}", user.getEmail());
        if (!spotifyAccountService.hasSpotifyLinked(user)) { return Optional.empty(); }
        return spotifyClient.getTopTracks(user, timeRange, limit);
    }
}