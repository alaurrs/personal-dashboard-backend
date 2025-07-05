package com.dashboard.backend.User.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import java.time.Instant;
import java.util.UUID;

/**
 * Représente un seul événement d'écoute dans l'historique d'un utilisateur.
 * C'est la "table de faits" centrale pour l'analyse des données d'écoute.
 *
 * Les index sur user_id et played_at sont cruciaux pour la performance des requêtes
 * d'analyse qui filtreront fréquemment sur ces colonnes.
 */
@Entity
@Table(name = "listening_history", indexes = {
        @Index(name = "idx_listeninghistory_user_id", columnList = "user_id"),
        @Index(name = "idx_listeninghistory_played_at", columnList = "played_at")
})
@Data
public class ListeningHistory {

    /**
     * Un identifiant unique pour chaque enregistrement d'écoute.
     * Utiliser un UUID est une bonne pratique pour les clés primaires qui n'ont pas de
     * signification métier.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;

    /**
     * L'utilisateur qui a effectué l'écoute.
     * C'est une relation Many-to-One car un utilisateur peut avoir plusieurs écoutes.
     * Le FetchType.LAZY est une optimisation critique pour éviter de charger l'objet User
     * à chaque fois qu'on récupère un enregistrement d'historique.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    @ToString.Exclude // Évite les problèmes de logs et de sérialisation en boucle
    @EqualsAndHashCode.Exclude
    private User user;

    /**
     * Le morceau qui a été écouté.
     * C'est une relation Many-to-One car un morceau peut être écouté plusieurs fois.
     * Le FetchType.LAZY est également essentiel ici pour la performance.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "track_id", nullable = false)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Track track;

    /**
     * L'horodatage exact de l'écoute, fourni par l'API Spotify.
     * Utiliser java.time.Instant est la meilleure pratique pour stocker des moments
     * précis dans le temps, car il est agnostique au fuseau horaire.
     * La colonne est non-nulle car une écoute sans horodatage n'a pas de sens.
     */
    @Column(name = "played_at", nullable = false)
    private Instant playedAt;
}