package com.dashboard.backend.service;

import com.dashboard.backend.User.model.*;
import com.dashboard.backend.User.repository.*;
import com.dashboard.backend.thirdparty.spotify.SpotifyClient;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyRecentlyPlayedDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyRecentlyPlayedDto.TrackDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyRecentlyPlayedDto.AlbumDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyRecentlyPlayedDto.ArtistDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SpotifyDataSyncService {

    private final SpotifyClient spotifyClient;
    private final ListeningHistoryRepository listeningHistoryRepository;
    private final TrackRepository trackRepository;
    private final AlbumRepository albumRepository;
    private final ArtistRepository artistRepository;

    @Transactional
    public void syncRecentlyPlayedForUser(User user) {
        log.info("▶️ Démarrage de la synchronisation de l'historique d'écoute pour {}", user.getEmail());

        // --- ÉTAPE 1: EXTRACT ---
        SpotifyRecentlyPlayedDto recentlyPlayed = spotifyClient.getRecentlyPlayed(user)
                .orElseThrow(() -> new RuntimeException("L'API Spotify n'a retourné aucun historique."));

        if (recentlyPlayed.items() == null || recentlyPlayed.items().isEmpty()) {
            log.info("✅ Aucune nouvelle écoute trouvée pour {}.", user.getEmail());
            return;
        }

        int newEntriesCount = 0;
        for (SpotifyRecentlyPlayedDto.Item item : recentlyPlayed.items()) {
            if (listeningHistoryRepository.existsByUserAndPlayedAt(user, item.playedAt())) {
                continue;
            }

            // --- ÉTAPE 2: TRANSFORM & LOAD (Dimensions) ---

            // 2a. Gérer tous les artistes du morceau. C'est maintenant la seule source de vérité pour les artistes.
            Set<Artist> artists = item.track().artists().stream()
                    .map(this::getOrCreateArtist)
                    .collect(Collectors.toSet());

            // 2b. Gérer l'album, en lui passant l'ensemble des artistes.
            Album album = getOrCreateAlbum(item.track().album(), artists);

            // 2c. Gérer le morceau, en lui passant aussi l'ensemble des artistes.
            Track track = getOrCreateTrack(item.track(), album, artists);

            // --- ÉTAPE 3: LOAD (Fait) ---
            ListeningHistory historyEntry = new ListeningHistory();
            historyEntry.setUser(user);
            historyEntry.setTrack(track);
            historyEntry.setPlayedAt(item.playedAt());

            listeningHistoryRepository.save(historyEntry);
            newEntriesCount++;
        }

        log.info("✅ Synchronisation terminée pour {}. {} nouvelles écoutes ajoutées.", user.getEmail(), newEntriesCount);
    }

    private Artist getOrCreateArtist(ArtistDto dto) {
        return artistRepository.findById(dto.id())
                .orElseGet(() -> {
                    log.debug("Création d'un nouvel artiste : {} ({})", dto.name(), dto.id());
                    Artist newArtist = new Artist();
                    newArtist.setId(dto.id());
                    newArtist.setName(dto.name());
                    return artistRepository.save(newArtist);
                });
    }

    /**
     * Trouve un album par son ID ou le crée s'il n'existe pas.
     * @param dto Le DTO de l'album.
     * @param artists L'ensemble des artistes à lier à l'album.
     * @return L'entité Album persistée.
     */
    private Album getOrCreateAlbum(AlbumDto dto, Set<Artist> artists) {
        return albumRepository.findById(dto.id())
                .orElseGet(() -> {
                    log.debug("Création d'un nouvel album : {} ({})", dto.name(), dto.id());
                    Album newAlbum = new Album();
                    newAlbum.setId(dto.id());
                    newAlbum.setName(dto.name());
                    // La modification clé est ici : on assigne l'ensemble des artistes.
                    newAlbum.setArtists(artists);
                    return albumRepository.save(newAlbum);
                });
    }

    private Track getOrCreateTrack(TrackDto dto, Album album, Set<Artist> artists) {
        return trackRepository.findById(dto.id())
                .orElseGet(() -> {
                    log.debug("Création d'un nouveau morceau : {} ({})", dto.name(), dto.id());
                    Track newTrack = new Track();
                    newTrack.setId(dto.id());
                    newTrack.setName(dto.name());
                    newTrack.setAlbum(album);
                    // La modification clé est ici : on assigne l'ensemble des artistes, pas un seul.
                    newTrack.setArtists(artists);
                    newTrack.setDurationMs(dto.durationMs());
                    return trackRepository.save(newTrack);
                });
    }
}
