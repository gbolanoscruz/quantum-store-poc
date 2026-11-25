package com.quantumstore.auth;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_accounts")
public class UserAccount extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(nullable = false, unique = true, length = 180)
    public String email;

    @Column(name = "full_name", nullable = false, length = 150)
    public String fullName;

    @Column(name = "password_hash", length = 255)
    public String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "auth_provider", nullable = false, length = 32)
    public AuthProvider authProvider;

    @Column(name = "google_sub", unique = true, length = 128)
    public String googleSub;

    @Column(nullable = false, length = 32)
    public String role = "CUSTOMER";

    @Column(nullable = false)
    public boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    public Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    public Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public static UserAccount findByEmailNormalized(String email) {
        if (email == null) {
            return null;
        }
        return find("email", email.trim().toLowerCase()).firstResult();
    }
}