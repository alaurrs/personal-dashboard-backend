package com.dashboard.backend.User.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "artists")
@Data
public class Artist {
    @Id
    @Column(name = "id")
    private String id; // L'ID Spotify de l'artiste

    @Column(nullable = false)
    private String name;

    @Column(name = "image_url", length = 2048)
    private String imageUrl;

    @Column(name = "genres", columnDefinition = "text[]")
    private List<String> genres;

    @ManyToMany(mappedBy = "artists", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Track> tracks = new HashSet<>();

    @ManyToMany(mappedBy = "artists", fetch = FetchType.LAZY)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Set<Album> albums = new HashSet<>();
}