package com.qldapm_L01.backend_api.Entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "users")
@Data
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String email;

    @Column(name = "role", nullable = false)
    private String role = "USER";

    @Column(name = "upload_count", nullable = false)
    private long uploadCount = 0;

    @Column(name = "download_count", nullable = false)
    private long downloadCount = 0;

    @Column(name = "status", nullable = false)
    private String status = "ACTIVE";

    @Column(name = "token_version", nullable = false)
    private int tokenVersion = 0;

    @Column(name = "banned_at")
    private Instant bannedAt;

    @OneToMany(mappedBy = "ownerId", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Document> documents;
}
