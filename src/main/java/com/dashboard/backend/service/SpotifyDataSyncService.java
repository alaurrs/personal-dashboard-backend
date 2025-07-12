package com.dashboard.backend.service;

import com.dashboard.backend.User.model.*;
import com.dashboard.backend.User.repository.*;
import com.dashboard.backend.rag.service.UserDocumentGenerationService;
import com.dashboard.backend.thirdparty.spotify.SpotifyClient;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyRecentlyPlayedDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyRecentlyPlayedDto.TrackDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyRecentlyPlayedDto.AlbumDto;
import com.dashboard.backend.thirdparty.spotify.dto.SpotifyRecentlyPlayedDto.ArtistDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
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
    private final UserDocumentGenerationService userDocumentGenerationService;

    @Transactional
    public void syncRecentlyPlayedForUser(User user) {
        log.info("▶️ Démarrage de la synchronisation de l'historique d'écoute pour {}", user.getEmail());

        // --- ÉTAPE 1: DÉTERMINER LE POINT DE DÉPART ---
        Instant lastPlayedAt = getLastPlayedTimestamp(user);
        if (lastPlayedAt != null) {
            log.info("🔄 Synchronisation incrémentale depuis le dernier morceau écouté: {}", lastPlayedAt);
        } else {
            log.info("🆕 Première synchronisation - récupération des morceaux récents");
        }

        // --- ÉTAPE 2: RÉCUPÉRATION COMPLÈTE AVEC PAGINATION ---
        int totalNewEntries = 0;
        boolean hasMoreData = true;
        Instant currentAfter = lastPlayedAt;

        while (hasMoreData) {
            // Récupérer les morceaux depuis le timestamp spécifié
            SpotifyRecentlyPlayedDto recentlyPlayed = spotifyClient.getRecentlyPlayed(user, currentAfter)
                    .orElseThrow(() -> new RuntimeException("L'API Spotify n'a retourné aucun historique."));

            if (recentlyPlayed.items() == null || recentlyPlayed.items().isEmpty()) {
                log.info("✅ Aucune nouvelle écoute trouvée pour {}.", user.getEmail());
                hasMoreData = false;
                continue;
            }

            int batchNewEntries = 0;
            Instant latestPlayedAt = null;

            for (SpotifyRecentlyPlayedDto.Item item : recentlyPlayed.items()) {
                // Vérifier si cette écoute existe déjà pour éviter les doublons
                if (listeningHistoryRepository.existsByUserAndPlayedAt(user, item.playedAt())) {
                    log.debug("⏭️ Écoute déjà existante ignorée: {} à {}",
                            item.track().name(), item.playedAt());
                    continue;
                }

                // Traiter et sauvegarder l'écoute
                processAndSaveListeningEntry(user, item);
                batchNewEntries++;

                // Garder trace du timestamp le plus récent
                if (latestPlayedAt == null || item.playedAt().isAfter(latestPlayedAt)) {
                    latestPlayedAt = item.playedAt();
                }
            }

            totalNewEntries += batchNewEntries;
            log.info("📦 Batch traité: {} nouvelles écoutes ajoutées", batchNewEntries);

            // Vérifier s'il faut continuer la pagination
            if (batchNewEntries == 0) {
                // Aucune nouvelle écoute dans ce batch, arrêter
                hasMoreData = false;
            } else if (recentlyPlayed.items().size() < 50) {
                // Moins de 50 résultats = fin des données
                hasMoreData = false;
            } else {
                // Préparer le prochain appel avec le timestamp le plus récent
                currentAfter = latestPlayedAt;
                log.debug("🔄 Préparation du prochain batch après: {}", currentAfter);
            }
        }

        userDocumentGenerationService.generateFromListeningHistory(user);

        log.info("✅ Synchronisation terminée pour {}. {} nouvelles écoutes ajoutées au total.",
                user.getEmail(), totalNewEntries);
    }

    /**
     * Récupère le timestamp de la dernière écoute synchronisée pour un utilisateur
     */
    private Instant getLastPlayedTimestamp(User user) {
        return listeningHistoryRepository.findTopByUserOrderByPlayedAtDesc(user)
                .map(ListeningHistory::getPlayedAt)
                .orElse(null);
    }

    /**
     * Enrichit un artiste existant avec ses genres récupérés depuis l'API Spotify.
     * Cette méthode est appelée pour s'assurer que les genres sont toujours à jour.
     */
    private void enrichArtistWithGenres(Artist artist, User user) {
        // Ne récupérer les genres que si l'artiste n'en a pas encore ou si ils sont vides
        if (artist.getGenres() == null || artist.getGenres().isEmpty()) {
            log.debug("Récupération des genres pour l'artiste: {} ({})", artist.getName(), artist.getId());

            spotifyClient.getArtistDetails(user, artist.getId())
                .ifPresent(artistDetails -> {
                    if (artistDetails.genres() != null && !artistDetails.genres().isEmpty()) {
                        artist.setGenres(artistDetails.genres());
                        artistRepository.save(artist);
                        log.debug("Genres ajoutés pour {}: {}", artist.getName(), artistDetails.genres());
                    }
                });
        }
    }

    /**
     * Traite et sauvegarde une entrée d'écoute avec enrichissement des genres
     */
    private void processAndSaveListeningEntry(User user, SpotifyRecentlyPlayedDto.Item item) {
        // --- ÉTAPE 2: TRANSFORM & LOAD (Dimensions) ---

        // 2a. Gérer tous les artistes du morceau et enrichir avec les genres
        Set<Artist> artists = item.track().artists().stream()
                .map(artistDto -> {
                    Artist artist = getOrCreateArtist(artistDto);
                    // Enrichir l'artiste avec ses genres
                    enrichArtistWithGenres(artist, user);
                    return artist;
                })
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
    }

    private Artist getOrCreateArtist(ArtistDto dto) {
        return artistRepository.findById(dto.id())
                .orElseGet(() -> {
                    log.debug("Création d'un nouvel artiste : {} ({})", dto.name(), dto.id());
                    Artist newArtist = new Artist();
                    newArtist.setId(dto.id());
                    newArtist.setName(dto.name());

                    // Ajouter l'URL de l'image si disponible
                    if (dto.images() != null && !dto.images().isEmpty()) {
                        newArtist.setImageUrl(dto.images().getFirst().url());
                    }

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

                    // Sauvegarder d'abord le track
                    Track savedTrack = trackRepository.save(newTrack);

                    // Puis associer automatiquement les genres via les artistes
                    associateGenresToTrack(savedTrack);

                    return savedTrack;
                });
    }

    /**
     * Associe automatiquement les genres d'un track basés sur ses artistes.
     * Les genres sont récupérés depuis tous les artistes associés au track.
     */
    private void associateGenresToTrack(Track track) {
        Set<String> allGenres = track.getArtists().stream()
                .filter(artist -> artist.getGenres() != null)
                .flatMap(artist -> artist.getGenres().stream())
                .collect(Collectors.toSet());

        if (!allGenres.isEmpty()) {
            track.setGenres(allGenres);
            trackRepository.save(track);
            log.debug("Genres associés au track '{}': {}", track.getName(), allGenres);
        }
    }
}
