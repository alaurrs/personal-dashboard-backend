package com.dashboard.backend.rag.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Entity
@Table(name = "user_documents")
public class UserDocument {
    @Id
    private UUID id;

    @Column(name = "user_id")
    private UUID userId;

    private String source;

    @Lob
    private String content;

    @Column(columnDefinition = "vector(1536)")
    private String embedding; // format texte JSON de float[] : "[0.12, 0.24, ...]"

    @Column(columnDefinition = "jsonb")
    private String metadata;

    private Instant createdAt;
}
