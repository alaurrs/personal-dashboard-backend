package com.dashboard.backend.User.model;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "tracks")
@Data
public class Track {
    @Id
    @Column(name = "id")
    private String id; // L'ID Spotify du morceau

    @Column(nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "album_id")
    private Album album;

    @ManyToMany(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    @JoinTable(
            name = "track_artists", // Nom de la table de jointure qui sera créée
            joinColumns = @JoinColumn(name = "track_id"), // Colonne qui fait référence à cette entité (Track)
            inverseJoinColumns = @JoinColumn(name = "artist_id") // Colonne qui fait référence à l'autre entité (Artist)
    )
    // Exclure ce champ de toString et equals/hashCode pour éviter les boucles infinies et les problèmes de performance
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Artist> artists = new HashSet<>();

    @Column(name = "duration_ms")
    private int durationMs;

    // ... autres champs comme la popularité, l'explicite, etc. ...
}