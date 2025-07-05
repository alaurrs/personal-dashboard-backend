package com.dashboard.backend.User.repository;

import com.dashboard.backend.User.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByEmail(String email);

    @Query("SELECT u FROM User u JOIN SpotifyAccount sa ON u.id = sa.user.id")
    List<User> findAllWithSpotifyAccount();

}
