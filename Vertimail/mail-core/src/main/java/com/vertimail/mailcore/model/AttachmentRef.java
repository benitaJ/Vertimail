package com.vertimail.mailcore.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Référence à une pièce jointe stockée dans data/attachments/.
 * Le fichier réel est nommé par son hash SHA-256.
 */
public class AttachmentRef {

    private String filename;
    private String sha256;

    @JsonCreator
    public AttachmentRef(
            @JsonProperty("filename") String filename,
            @JsonProperty("sha256") String sha256) {
        this.filename = filename;
        this.sha256 = sha256;
    }

    public AttachmentRef() {
        // constructeur par défaut pour Jackson
    }

    public String getFilename() {
        return filename;
    }

    public String getSha256() {
        return sha256;
    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }
}

