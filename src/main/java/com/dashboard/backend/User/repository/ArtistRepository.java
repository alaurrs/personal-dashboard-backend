package com.dashboard.backend.User.repository;

import com.dashboard.backend.User.model.Artist;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ArtistRepository extends JpaRepository<Artist, String> {
}
