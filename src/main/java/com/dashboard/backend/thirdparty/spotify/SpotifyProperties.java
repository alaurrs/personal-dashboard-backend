package com.dashboard.backend.thirdparty.spotify;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spotify")
@Data
public class SpotifyProperties {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
}
