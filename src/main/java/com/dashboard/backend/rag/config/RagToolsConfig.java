package com.dashboard.backend.rag.config;

import com.dashboard.backend.rag.tools.SpotifyTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Description;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Configuration
public class RagToolsConfig {

    @Bean
    @Description("Obtient le top des artistes les plus écoutés pour un utilisateur donné.")
    public Function<SpotifyTool.SqlQueryRequest, String> executeListeningHistoryQuery(SpotifyTool spotifyTool) {
        return spotifyTool::executeListeningHistoryQuery;
    }

}
