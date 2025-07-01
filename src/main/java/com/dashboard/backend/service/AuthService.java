package com.dashboard.backend.service;

import com.dashboard.backend.User.AuthResponse;
import com.dashboard.backend.User.LoginRequest;
import com.dashboard.backend.User.RegisterRequest;
import com.dashboard.backend.User.model.User;
import com.dashboard.backend.User.repository.UserRepository;
import com.dashboard.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final BCryptPasswordEncoder bCryptPasswordEncoder = new BCryptPasswordEncoder();


    public AuthResponse register(RegisterRequest registerRequest) {
        User user = new User();
        user.setEmail(registerRequest.email());
        user.setPasswordHash(bCryptPasswordEncoder.encode(registerRequest.password()));
        user.setFullName(registerRequest.fullName());

        userRepository.save(user);
        return new AuthResponse(jwtService.generateToken(user.getEmail(), user.getFullName(), user.getId()));
    }

    public AuthResponse login(LoginRequest loginRequest) {
        User user = userRepository.findByEmail(loginRequest.email())
                .orElseThrow(() -> new UsernameNotFoundException(loginRequest.email()));

        if (!bCryptPasswordEncoder.matches(loginRequest.password(), user.getPasswordHash())) {
            throw new UsernameNotFoundException("Invalid credentials");
        }

        return new AuthResponse(jwtService.generateToken(user.getEmail(), user.getFullName(), user.getId()));
    }
}
