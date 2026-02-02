package fr.uge.webmail.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Représente un utilisateur du système de messagerie.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class User {
    
    @JsonProperty("username")
    private String username;
    
    @JsonProperty("passwordHash")
    private String passwordHash;
    
    @JsonProperty("recoveryCode")
    private String recoveryCode;
    
    @JsonProperty("createdAt")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime createdAt;
    
    @JsonProperty("lastLogin")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime lastLogin;
    
    /**
     * Constructeur par défaut pour Jackson.
     */
    public User() {
    }
    
    /**
     * Constructeur avec username et password hash.
     */
    public User(String username, String passwordHash, String recoveryCode) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.recoveryCode = recoveryCode;
        this.createdAt = LocalDateTime.now();
    }
    
    // Getters et Setters
    
    public String getUsername() {
        return username;
    }
    
    public void setUsername(String username) {
        this.username = username;
    }
    
    public String getPasswordHash() {
        return passwordHash;
    }
    
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }
    
    public String getRecoveryCode() {
        return recoveryCode;
    }
    
    public void setRecoveryCode(String recoveryCode) {
        this.recoveryCode = recoveryCode;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getLastLogin() {
        return lastLogin;
    }
    
    public void setLastLogin(LocalDateTime lastLogin) {
        this.lastLogin = lastLogin;
    }
    
    /**
     * Met à jour la date de dernière connexion.
     */
    public void updateLastLogin() {
        this.lastLogin = LocalDateTime.now();
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        User user = (User) o;
        return Objects.equals(username, user.username);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(username);
    }
    
    @Override
    public String toString() {
        return "User{" +
                "username='" + username + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
