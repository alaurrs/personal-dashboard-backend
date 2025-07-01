package com.dashboard.backend.User.controller;

import com.dashboard.backend.User.dto.UserDto;
import com.dashboard.backend.User.model.User;
import com.dashboard.backend.User.repository.UserRepository;
import com.dashboard.backend.security.JwtService;
import com.dashboard.backend.testsupport.DotenvInitializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ContextConfiguration(initializers = DotenvInitializer.class)
class UserControllerIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    private String baseUrl;
    private User testUser;

    @BeforeEach
    void setUp() {
        baseUrl = "http://localhost:" + port;

        // Créer et sauvegarder un utilisateur de test
        testUser = new User();
        testUser.setEmail("testuser@example.com");
        testUser.setPasswordHash("fakeEncodedPassword"); // mot de passe encodé fictif
        testUser.setFullName("Test Name");

        userRepository.save(testUser);
    }

    @Test
    void shouldReturnCurrentUser_whenJwtIsValid() {
        // Générer le JWT via la méthode actuelle
        String jwt = jwtService.generateToken(testUser.getEmail(), testUser.getFullName(), testUser.getId());

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(jwt);
        HttpEntity<Void> request = new HttpEntity<>(headers);

        ResponseEntity<UserDto> response = new RestTemplate().exchange(
                baseUrl + "/api/user/me",
                HttpMethod.GET,
                request,
                UserDto.class
        );

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().email()).isEqualTo(testUser.getEmail());
        assertThat(response.getBody().fullName()).isEqualTo(testUser.getFullName());
    }
}