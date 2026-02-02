package fr.uge.webmail.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Représente une pièce jointe d'un courrier électronique.
 * La pièce jointe est référencée par son hash SHA-256 pour éviter les doublons.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Attachment {
    
    @JsonProperty("filename")
    private String filename;
    
    @JsonProperty("sha256")
    private String sha256;
    
    @JsonProperty("contentType")
    private String contentType;
    
    @JsonProperty("size")
    private long size;
    
    /**
     * Constructeur par défaut pour Jackson.
     */
    public Attachment() {
    }
    
    /**
     * Constructeur complet.
     */
    public Attachment(String filename, String sha256, String contentType, long size) {
        this.filename = filename;
        this.sha256 = sha256;
        this.contentType = contentType;
        this.size = size;
    }
    
    // Getters et Setters
    
    public String getFilename() {
        return filename;
    }
    
    public void setFilename(String filename) {
        this.filename = filename;
    }
    
    public String getSha256() {
        return sha256;
    }
    
    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }
    
    public String getContentType() {
        return contentType;
    }
    
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
    
    public long getSize() {
        return size;
    }
    
    public void setSize(long size) {
        this.size = size;
    }
    
    /**
     * Retourne la taille formatée de manière lisible.
     */
    public String getFormattedSize() {
        if (size < 1024) {
            return size + " B";
        } else if (size < 1024 * 1024) {
            return String.format("%.1f KB", size / 1024.0);
        } else if (size < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", size / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Attachment that = (Attachment) o;
        return Objects.equals(sha256, that.sha256);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(sha256);
    }
    
    @Override
    public String toString() {
        return "Attachment{" +
                "filename='" + filename + '\'' +
                ", sha256='" + sha256 + '\'' +
                ", size=" + getFormattedSize() +
                '}';
    }
}
