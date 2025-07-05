package com.dashboard.backend.User.repository;

import com.dashboard.backend.User.model.Track;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrackRepository extends JpaRepository<Track, String> {
}
