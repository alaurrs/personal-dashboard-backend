package com.dashboard.backend.thirdparty.supabase.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class SupabaseService {

    private final JdbcTemplate jdbc;

    public List<String> queryRelevantDocuments(UUID userId, float[] embedding) {
        String sql = """
    SELECT content
    FROM user_documents
    WHERE user_id = ?
    AND summary_type IN ('monthly', 'hourly', 'top_tracks_global')
    ORDER BY embedding <-> CAST(? AS vector)
    LIMIT 10
""";

        String pgVector = IntStream.range(0, embedding.length)
                .mapToObj(i -> String.format(Locale.US, "%.6f", embedding[i]))
                .collect(Collectors.joining(", ", "[", "]"));

        return jdbc.query(sql, new Object[]{userId, pgVector}, (rs, i) -> rs.getString("content"));
    }

    private String arrayToPgvector(float[] embedding) {
        return java.util.stream.IntStream.range(0, embedding.length)
                .mapToObj(i -> String.format(Locale.US, "%.6f", embedding[i]))
                .collect(Collectors.joining(", ", "[", "]"));
    }
}
